package matrix

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

/** Peek/poke harness around the RawModule FrameMatrix top. */
class FrameMatrixHarness extends Module {
  val io = IO(new Bundle {
    val in = Input(UInt(IoMap.PayloadWidth.W))
    val out = Output(UInt(IoMap.PayloadWidth.W))
    val oe = Output(UInt(IoMap.PayloadWidth.W))
  })
  val dut = Module(new FrameMatrix(IoMap.PayloadWidth))
  dut.clock := clock
  dut.reset := reset.asBool
  dut.io_in := io.in
  io.out := dut.io_out
  io.oe := dut.io_oe
}

class FrameMatrixSpec extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "FrameMatrix"

  private def cmd(
      data: Int = 0,
      row: Int = 0,
      col: Int = 0,
      opcode: Int = 0,
      start: Boolean = false
  ): BigInt = {
    BigInt(data & 0xff) |
      (BigInt(row & 0x3) << IoMap.RowLo) |
      (BigInt(col & 0x3) << IoMap.ColLo) |
      (BigInt(opcode & 0xf) << IoMap.OpcodeLo) |
      (if (start) BigInt(1) << IoMap.StartBit else BigInt(0))
  }

  private def rdata(out: BigInt): BigInt =
    (out >> IoMap.RdataLo) & ((BigInt(1) << 32) - 1)

  private def done(out: BigInt): Boolean =
    ((out >> IoMap.DoneBit) & 1) == 1

  private def busy(out: BigInt): Boolean =
    ((out >> IoMap.BusyBit) & 1) == 1

  private def issue(dut: FrameMatrixHarness, opcode: Int, row: Int, col: Int, data: Int): Unit = {
    dut.io.in.poke(cmd(data, row, col, opcode, start = true).U)
    dut.clock.step(1)
    dut.io.in.poke(cmd(data, row, col, opcode, start = false).U)
  }

  private def waitDone(dut: FrameMatrixHarness, limit: Int = 128): Unit = {
    var cycles = 0
    while (!done(dut.io.out.peekInt()) && cycles < limit) {
      dut.clock.step(1)
      cycles += 1
    }
    assert(done(dut.io.out.peekInt()), s"timeout waiting for done after $cycles")
  }

  it should "enable only result/status OE bits" in {
    test(new FrameMatrixHarness) { dut =>
      dut.io.in.poke(0.U)
      dut.clock.step(2)
      val oe = dut.io.oe.peekInt()
      val expected =
        ((BigInt(1) << (IoMap.OutputHi - IoMap.OutputLo + 1)) - 1) << IoMap.OutputLo
      assert(oe == expected, f"oe=$oe%X expected=$expected%X")
      // Command bits must stay released so the outside world can drive them.
      assert((oe & ((BigInt(1) << (IoMap.StartBit + 1)) - 1)) == 0)
    }
  }

  it should "accept WR_A / RD_A through the payload map" in {
    test(new FrameMatrixHarness) { dut =>
      dut.io.in.poke(0.U)
      dut.clock.step(2)

      issue(dut, Opcodes.WR_A_LIT, 2, 1, 0x5a)
      waitDone(dut)
      dut.clock.step(1)

      issue(dut, Opcodes.RD_A_LIT, 2, 1, 0)
      waitDone(dut)
      assert(rdata(dut.io.out.peekInt()) == 0x5a)
      assert(!busy(dut.io.out.peekInt()))
    }
  }

  it should "run FILL_A then MATMUL identity path" in {
    test(new FrameMatrixHarness) { dut =>
      dut.io.in.poke(0.U)
      dut.clock.step(2)

      // A = all ones via FILL_A is too coarse; write identity manually.
      for (i <- 0 until 4) {
        issue(dut, Opcodes.WR_A_LIT, i, i, 1)
        waitDone(dut)
        dut.clock.step(1)
      }
      for (r <- 0 until 4; c <- 0 until 4) {
        issue(dut, Opcodes.WR_B_LIT, r, c, r * 4 + c + 1)
        waitDone(dut)
        dut.clock.step(1)
      }
      issue(dut, Opcodes.MATMUL_LIT, 0, 0, 0)
      waitDone(dut, 128)
      dut.clock.step(1)

      for (r <- 0 until 4; c <- 0 until 4) {
        issue(dut, Opcodes.RD_C_LIT, r, c, 0)
        waitDone(dut)
        assert(
          rdata(dut.io.out.peekInt()) == r * 4 + c + 1,
          s"C($r,$c) mismatch"
        )
        dut.clock.step(1)
      }
    }
  }
}
