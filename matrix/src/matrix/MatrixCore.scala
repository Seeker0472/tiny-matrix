package matrix

import chisel3._
import chisel3.util._

class MatrixCoreIO extends Bundle {
  val opcode = Input(UInt(4.W))
  val row = Input(UInt(2.W))
  val col = Input(UInt(2.W))
  val data = Input(UInt(8.W))
  val start = Input(Bool())
  val rdata = Output(UInt(32.W))
  val done = Output(Bool())
  val busy = Output(Bool())
}

/** 4x4 matrix core: classic RTL split of next-state (comb) and registers. */
class MatrixCore extends Module {
  val io = IO(new MatrixCoreIO)

  val n = 4
  val last = (n - 1).U(2.W)

  // ---- registers ----
  val a = RegInit(VecInit(Seq.fill(n)(VecInit(Seq.fill(n)(0.U(8.W))))))
  val b = RegInit(VecInit(Seq.fill(n)(VecInit(Seq.fill(n)(0.U(8.W))))))
  val c = RegInit(VecInit(Seq.fill(n)(VecInit(Seq.fill(n)(0.U(32.W))))))

  val busy = RegInit(false.B)
  val done = RegInit(false.B)
  val rdata = RegInit(0.U(32.W))
  val op = RegInit(0.U(4.W))
  val i = RegInit(0.U(2.W))
  val j = RegInit(0.U(2.W))
  val k = RegInit(0.U(2.W))
  val latchedRow = RegInit(0.U(2.W))
  val latchedCol = RegInit(0.U(2.W))
  val latchedData = RegInit(0.U(8.W))

  io.rdata := rdata
  io.done := done
  io.busy := busy

  // ---- next-state defaults (hold / done pulse low) ----
  val aNext = WireDefault(a)
  val bNext = WireDefault(b)
  val cNext = WireDefault(c)
  val busyNext = WireDefault(busy)
  val doneNext = WireDefault(false.B)
  val rdataNext = WireDefault(rdata)
  val opNext = WireDefault(op)
  val iNext = WireDefault(i)
  val jNext = WireDefault(j)
  val kNext = WireDefault(k)
  val latchedRowNext = WireDefault(latchedRow)
  val latchedColNext = WireDefault(latchedCol)
  val latchedDataNext = WireDefault(latchedData)

  // ---- ALU operand mux ----
  val alu = Module(new MatrixAlu)
  alu.io.a := Mux(
    op === Opcodes.MATMUL,
    a(i)(k),
    Mux(op === Opcodes.TRANS, a(j)(i), a(i)(j))
  )
  alu.io.b := Mux(op === Opcodes.MATMUL, b(k)(j), b(i)(j))
  alu.io.c := c(i)(j)

  // Element-wise / transpose write data into C.
  val elemC = MuxLookup(op, c(i)(j))(
    Seq(
      Opcodes.MADD -> alu.io.madd,
      Opcodes.MSUB -> alu.io.msub,
      Opcodes.HMUL -> alu.io.hmul,
      Opcodes.TRANS -> alu.io.a
    )
  )

  val matmulC = Mux(k === 0.U, alu.io.hmul, alu.io.mac)

  val isElemWalk =
    op === Opcodes.MADD || op === Opcodes.MSUB || op === Opcodes.HMUL || op === Opcodes.TRANS

  // (i,j) raster: returns (iNext, jNext, finished)
  def nextIj(iCur: UInt, jCur: UInt): (UInt, UInt, Bool) = {
    val jIsLast = jCur === last
    val iIsLast = iCur === last
    val jN = Mux(jIsLast, 0.U, jCur + 1.U)
    val iN = Mux(jIsLast && !iIsLast, iCur + 1.U, iCur)
    val fin = jIsLast && iIsLast
    (iN, jN, fin)
  }

  // ---- control / datapath next-state ----
  when(!busy) {
    when(io.start) {
      busyNext := true.B
      opNext := io.opcode
      latchedRowNext := io.row
      latchedColNext := io.col
      latchedDataNext := io.data
      iNext := 0.U
      jNext := 0.U
      kNext := 0.U
    }
  }.otherwise {
    when(op === Opcodes.WR_A) {
      aNext(latchedRow)(latchedCol) := latchedData
      busyNext := false.B
      doneNext := true.B
    }.elsewhen(op === Opcodes.WR_B) {
      bNext(latchedRow)(latchedCol) := latchedData
      busyNext := false.B
      doneNext := true.B
    }.elsewhen(op === Opcodes.RD_C) {
      rdataNext := c(latchedRow)(latchedCol)
      busyNext := false.B
      doneNext := true.B
    }.elsewhen(op === Opcodes.RD_A) {
      rdataNext := a(latchedRow)(latchedCol)
      busyNext := false.B
      doneNext := true.B
    }.elsewhen(op === Opcodes.CLEAR) {
      aNext := VecInit(Seq.fill(n)(VecInit(Seq.fill(n)(0.U(8.W)))))
      bNext := VecInit(Seq.fill(n)(VecInit(Seq.fill(n)(0.U(8.W)))))
      cNext := VecInit(Seq.fill(n)(VecInit(Seq.fill(n)(0.U(32.W)))))
      rdataNext := 0.U
      busyNext := false.B
      doneNext := true.B
    }.elsewhen(op === Opcodes.FILL_A) {
      aNext := VecInit(Seq.fill(n)(VecInit(Seq.fill(n)(latchedData))))
      busyNext := false.B
      doneNext := true.B
    }.elsewhen(op === Opcodes.FILL_B) {
      bNext := VecInit(Seq.fill(n)(VecInit(Seq.fill(n)(latchedData))))
      busyNext := false.B
      doneNext := true.B
    }.elsewhen(isElemWalk) {
      cNext(i)(j) := elemC
      val (iN, jN, fin) = nextIj(i, j)
      iNext := iN
      jNext := jN
      when(fin) {
        busyNext := false.B
        doneNext := true.B
      }
    }.elsewhen(op === Opcodes.MATMUL) {
      cNext(i)(j) := matmulC
      when(k === last) {
        kNext := 0.U
        val (iN, jN, fin) = nextIj(i, j)
        iNext := iN
        jNext := jN
        when(fin) {
          busyNext := false.B
          doneNext := true.B
        }
      }.otherwise {
        kNext := k + 1.U
      }
    }.otherwise {
      // unknown opcode: complete immediately
      busyNext := false.B
      doneNext := true.B
    }
  }

  // ---- register update ----
  a := aNext
  b := bNext
  c := cNext
  busy := busyNext
  done := doneNext
  rdata := rdataNext
  op := opNext
  i := iNext
  j := jNext
  k := kNext
  latchedRow := latchedRowNext
  latchedCol := latchedColNext
  latchedData := latchedDataNext
}
