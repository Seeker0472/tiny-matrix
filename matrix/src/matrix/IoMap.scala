package matrix

import chisel3._
import chisel3.util.Cat

/** mpc-frame payload bit map for FrameMatrix (66-bit io_in/io_out/io_oe).
  *
  * Inputs and outputs intentionally do not overlap so the testbench can drive
  * commands while the design drives status/result bits.
  *
  * {{{
  * io_in[7:0]    data
  * io_in[9:8]    row
  * io_in[11:10]  col
  * io_in[15:12]  opcode
  * io_in[16]     start
  * io_out[48:17] rdata
  * io_out[49]    done
  * io_out[50]    busy
  * }}}
  */
object IoMap {
  val PayloadWidth = 66

  val DataLo = 0
  val DataHi = 7
  val RowLo = 8
  val RowHi = 9
  val ColLo = 10
  val ColHi = 11
  val OpcodeLo = 12
  val OpcodeHi = 15
  val StartBit = 16

  val RdataLo = 17
  val RdataHi = 48
  val DoneBit = 49
  val BusyBit = 50

  val OutputLo = RdataLo
  val OutputHi = BusyBit

  def encodeCmd(
      data: UInt,
      row: UInt,
      col: UInt,
      opcode: UInt,
      start: Bool
  ): UInt = {
    Cat(
      0.U((PayloadWidth - StartBit - 1).W),
      start.asUInt,
      opcode(3, 0),
      col(1, 0),
      row(1, 0),
      data(7, 0)
    )
  }
}
