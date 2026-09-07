package matrix

import chisel3._

/** Combinational 8-bit element ALU used by the 4x4 walker. */
class MatrixAlu extends Module {
  val io = IO(new Bundle {
    val a = Input(UInt(8.W))
    val b = Input(UInt(8.W))
    val c = Input(UInt(32.W))
    val product = Output(UInt(16.W))
    val madd = Output(UInt(32.W))
    val msub = Output(UInt(32.W))
    val hmul = Output(UInt(32.W))
    val mac = Output(UInt(32.W))
  })

  // Force 32-bit operands so firtool keeps modular 32-bit add/sub (not 9-bit).
  val a32 = WireDefault(0.U(32.W))
  val b32 = WireDefault(0.U(32.W))
  a32 := io.a
  b32 := io.b

  val prod16 = io.a * io.b
  val prod32 = WireDefault(0.U(32.W))
  prod32 := prod16

  io.product := prod16
  io.madd := a32 + b32
  io.msub := a32 - b32
  io.hmul := prod32
  io.mac := io.c + prod32
}
