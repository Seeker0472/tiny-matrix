package matrix

import chisel3._

object Opcodes {
  val WR_A: UInt = 0x0.U(4.W)
  val WR_B: UInt = 0x1.U(4.W)
  val RD_C: UInt = 0x2.U(4.W)
  val CLEAR: UInt = 0x3.U(4.W)
  val MADD: UInt = 0x4.U(4.W)
  val MSUB: UInt = 0x5.U(4.W)
  val HMUL: UInt = 0x6.U(4.W)
  val MATMUL: UInt = 0x7.U(4.W)
  val TRANS: UInt = 0x8.U(4.W)
  val FILL_A: UInt = 0x9.U(4.W)
  val FILL_B: UInt = 0xA.U(4.W)
  val RD_A: UInt = 0xB.U(4.W)

  val WR_A_LIT = 0x0
  val WR_B_LIT = 0x1
  val RD_C_LIT = 0x2
  val CLEAR_LIT = 0x3
  val MADD_LIT = 0x4
  val MSUB_LIT = 0x5
  val HMUL_LIT = 0x6
  val MATMUL_LIT = 0x7
  val TRANS_LIT = 0x8
  val FILL_A_LIT = 0x9
  val FILL_B_LIT = 0xA
  val RD_A_LIT = 0xB
}
