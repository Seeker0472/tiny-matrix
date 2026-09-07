package matrix

import chisel3._
import chisel3.util.Cat

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import scala.util.matching.Regex

/** mpc-frame user design top. Ports match the FrameTop payload contract.
  *
  * @param ioWidth Width of `io_in` / `io_out` / `io_oe`. Must be >= [[IoMap.PayloadWidth]];
  *                bits above the payload are ignored on input and driven 0 on output.
  *
  * Reset is synchronous and active-high, matching FrameDesignControl.
  * Port names are flat (not nested under `io`) so generated SV matches the
  * registry wrapper expectations.
  *
  * Elaborate rewrites the emitted top header to `parameter int IO_WIDTH` so
  * mpc-frame can pass `.IO_WIDTH(IO_WIDTH)` from `design.json`.
  */
class FrameMatrix(val ioWidth: Int = IoMap.PayloadWidth) extends RawModule {
  require(
    ioWidth >= IoMap.PayloadWidth,
    s"ioWidth=$ioWidth must be >= payload width ${IoMap.PayloadWidth}"
  )

  val clock = IO(Input(Clock()))
  val reset = IO(Input(Bool()))
  val io_in = IO(Input(UInt(ioWidth.W)))
  val io_out = IO(Output(UInt(ioWidth.W)))
  val io_oe = IO(Output(UInt(ioWidth.W)))

  // Synchronous active-high reset (Bool, not AsyncReset).
  withClockAndReset(clock, reset) {
    val core = Module(new MatrixCore)
    val payloadIn = io_in(IoMap.PayloadWidth - 1, 0)

    core.io.data := payloadIn(IoMap.DataHi, IoMap.DataLo)
    core.io.row := payloadIn(IoMap.RowHi, IoMap.RowLo)
    core.io.col := payloadIn(IoMap.ColHi, IoMap.ColLo)
    core.io.opcode := payloadIn(IoMap.OpcodeHi, IoMap.OpcodeLo)
    core.io.start := payloadIn(IoMap.StartBit)

    val payloadOut = WireDefault(0.U(IoMap.PayloadWidth.W))
    val payloadOe = WireDefault(0.U(IoMap.PayloadWidth.W))

    payloadOut := (core.io.rdata << IoMap.RdataLo) |
      (core.io.done.asUInt << IoMap.DoneBit) |
      (core.io.busy.asUInt << IoMap.BusyBit)

    // Enable only result/status bits; command bits stay released for the TB.
    val outEnWidth = IoMap.OutputHi - IoMap.OutputLo + 1
    payloadOe := ((1.U << outEnWidth) - 1.U) << IoMap.OutputLo

    val padHi = ioWidth - IoMap.PayloadWidth
    if (padHi == 0) {
      io_out := payloadOut
      io_oe := payloadOe
    } else {
      io_out := Cat(0.U(padHi.W), payloadOut)
      io_oe := Cat(0.U(padHi.W), payloadOe)
    }
  }
}

object FrameMatrix {
  val TopModule = "FrameMatrix"

  /** Rewrite firtool output so the top module exposes mpc-frame's IO_WIDTH. */
  def injectIoWidthParameter(sv: String, defaultWidth: Int): String = {
    val msb = defaultWidth - 1
    val pattern: Regex =
      raw"""(?s)module\s+$TopModule\s*\(\s*input\s+clock,\s*reset,\s*input\s+\[$msb:0\]\s+io_in,\s*output\s+\[$msb:0\]\s+io_out,\s*io_oe\s*\)\s*;""".r

    val replacement =
      s"""module $TopModule #(
  parameter int IO_WIDTH = $defaultWidth
) (
  input                  clock,
                         reset,
  input  [IO_WIDTH-1:0]  io_in,
  output [IO_WIDTH-1:0]  io_out,
                         io_oe
);"""

    pattern.findFirstIn(sv) match {
      case Some(_) => pattern.replaceFirstIn(sv, Regex.quoteReplacement(replacement))
      case None =>
        throw new IllegalStateException(
          s"failed to locate $TopModule port list for IO_WIDTH injection (width=$defaultWidth)"
        )
    }
  }
}

object Elaborate extends App {
  val firtoolOptions = Array(
    "--disable-all-randomization",
    "--strip-debug-info",
    "--lowering-options=" + List(
      "disallowLocalVariables",
      "disallowPackedArrays",
      "locationInfoStyle=wrapInAtSquareBracket"
    ).mkString(",")
  )

  val ioWidth = IoMap.PayloadWidth
  circt.stage.ChiselStage.emitSystemVerilogFile(
    new FrameMatrix(ioWidth),
    args,
    firtoolOptions
  )

  // Mill / ChiselStage place the file under --target-dir when that flag is present.
  val targetDir = args
    .sliding(2)
    .collectFirst { case Array("--target-dir", dir) => dir }
    .getOrElse(".")
  val svPath = Paths.get(targetDir, s"${FrameMatrix.TopModule}.sv")
  val original = new String(Files.readAllBytes(svPath), StandardCharsets.UTF_8)
  val rewritten = FrameMatrix.injectIoWidthParameter(original, ioWidth)
  Files.write(svPath, rewritten.getBytes(StandardCharsets.UTF_8))
}
