package matrix

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class MatrixCoreSpec extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "MatrixCore"

  private def idle(dut: MatrixCore): Unit = {
    dut.io.start.poke(false.B)
    dut.io.opcode.poke(0.U)
    dut.io.row.poke(0.U)
    dut.io.col.poke(0.U)
    dut.io.data.poke(0.U)
  }

  private def issue(
      dut: MatrixCore,
      opcode: Int,
      row: Int = 0,
      col: Int = 0,
      data: Int = 0
  ): Unit = {
    dut.io.opcode.poke(opcode.U)
    dut.io.row.poke(row.U)
    dut.io.col.poke(col.U)
    dut.io.data.poke(data.U)
    dut.io.start.poke(true.B)
    dut.clock.step(1)
    dut.io.start.poke(false.B)
  }

  private def waitDone(dut: MatrixCore, limit: Int = 128): Unit = {
    var cycles = 0
    while (!dut.io.done.peekBoolean() && cycles < limit) {
      dut.clock.step(1)
      cycles += 1
    }
    assert(dut.io.done.peekBoolean(), s"timed out after $cycles cycles waiting for done")
  }

  private def runOp(
      dut: MatrixCore,
      opcode: Int,
      row: Int = 0,
      col: Int = 0,
      data: Int = 0,
      limit: Int = 128
  ): Unit = {
    issue(dut, opcode, row, col, data)
    waitDone(dut, limit)
  }

  private def writeA(dut: MatrixCore, row: Int, col: Int, data: Int): Unit =
    runOp(dut, Opcodes.WR_A_LIT, row, col, data)

  private def writeB(dut: MatrixCore, row: Int, col: Int, data: Int): Unit =
    runOp(dut, Opcodes.WR_B_LIT, row, col, data)

  private def readC(dut: MatrixCore, row: Int, col: Int): BigInt = {
    runOp(dut, Opcodes.RD_C_LIT, row, col)
    dut.io.rdata.peekInt()
  }

  private def readA(dut: MatrixCore, row: Int, col: Int): BigInt = {
    runOp(dut, Opcodes.RD_A_LIT, row, col)
    dut.io.rdata.peekInt()
  }

  private def loadMatrixA(dut: MatrixCore, values: Seq[Seq[Int]]): Unit = {
    for (r <- 0 until 4; c <- 0 until 4) writeA(dut, r, c, values(r)(c))
  }

  private def loadMatrixB(dut: MatrixCore, values: Seq[Seq[Int]]): Unit = {
    for (r <- 0 until 4; c <- 0 until 4) writeB(dut, r, c, values(r)(c))
  }

  private def expectMatrixC(dut: MatrixCore, expected: Seq[Seq[Int]]): Unit = {
    for (r <- 0 until 4; c <- 0 until 4) {
      val got = readC(dut, r, c)
      assert(got == expected(r)(c), s"C($r,$c)=$got expected ${expected(r)(c)}")
    }
  }

  it should "reset to idle with zero status" in {
    test(new MatrixCore) { dut =>
      idle(dut)
      dut.clock.step(2)
      dut.io.busy.expect(false.B)
      dut.io.done.expect(false.B)
      dut.io.rdata.expect(0.U)
    }
  }

  it should "write and read matrix A cells" in {
    test(new MatrixCore) { dut =>
      idle(dut)
      writeA(dut, 1, 2, 0xab)
      writeA(dut, 0, 0, 0x11)
      assert(readA(dut, 1, 2) == 0xab)
      assert(readA(dut, 0, 0) == 0x11)
      assert(readA(dut, 3, 3) == 0)
    }
  }

  it should "fill A and B then clear all banks" in {
    test(new MatrixCore) { dut =>
      idle(dut)
      runOp(dut, Opcodes.FILL_A_LIT, data = 7)
      runOp(dut, Opcodes.FILL_B_LIT, data = 9)
      for (r <- 0 until 4; c <- 0 until 4) {
        assert(readA(dut, r, c) == 7)
      }
      runOp(dut, Opcodes.MADD_LIT, limit = 32)
      assert(readC(dut, 0, 0) == 16)
      runOp(dut, Opcodes.CLEAR_LIT)
      for (r <- 0 until 4; c <- 0 until 4) {
        assert(readA(dut, r, c) == 0)
        assert(readC(dut, r, c) == 0)
      }
    }
  }

  it should "perform element-wise MADD" in {
    test(new MatrixCore) { dut =>
      idle(dut)
      val a = Seq(
        Seq(1, 2, 3, 4),
        Seq(5, 6, 7, 8),
        Seq(9, 10, 11, 12),
        Seq(13, 14, 15, 16)
      )
      val b = Seq(
        Seq(16, 15, 14, 13),
        Seq(12, 11, 10, 9),
        Seq(8, 7, 6, 5),
        Seq(4, 3, 2, 1)
      )
      loadMatrixA(dut, a)
      loadMatrixB(dut, b)

      runOp(dut, Opcodes.MADD_LIT, limit = 32)
      expectMatrixC(
        dut,
        for (r <- 0 until 4) yield for (c <- 0 until 4) yield a(r)(c) + b(r)(c)
      )
    }
  }

  it should "compute MSUB with 32-bit wrap" in {
    test(new MatrixCore) { dut =>
      idle(dut)
      writeA(dut, 0, 0, 3)
      writeB(dut, 0, 0, 5)
      // Fill remaining with zeros already
      runOp(dut, Opcodes.MSUB_LIT, limit = 32)
      val got = readC(dut, 0, 0)
      assert(got == BigInt("fffffffe", 16), s"got $got")
    }
  }

  it should "compute HMUL element-wise products" in {
    test(new MatrixCore) { dut =>
      idle(dut)
      writeA(dut, 0, 0, 7)
      writeB(dut, 0, 0, 6)
      writeA(dut, 1, 1, 255)
      writeB(dut, 1, 1, 2)
      runOp(dut, Opcodes.HMUL_LIT, limit = 32)
      assert(readC(dut, 0, 0) == 42)
      assert(readC(dut, 1, 1) == 510)
      assert(readC(dut, 2, 2) == 0)
    }
  }

  it should "transpose A into C" in {
    test(new MatrixCore) { dut =>
      idle(dut)
      val a = Seq(
        Seq(1, 2, 3, 4),
        Seq(5, 6, 7, 8),
        Seq(9, 10, 11, 12),
        Seq(13, 14, 15, 16)
      )
      loadMatrixA(dut, a)
      runOp(dut, Opcodes.TRANS_LIT, limit = 32)
      expectMatrixC(
        dut,
        for (r <- 0 until 4) yield for (c <- 0 until 4) yield a(c)(r)
      )
    }
  }

  it should "compute 4x4 MATMUL into C" in {
    test(new MatrixCore) { dut =>
      idle(dut)
      // A = I * scale, B = simple increasing matrix
      val a = Seq(
        Seq(1, 0, 0, 0),
        Seq(0, 1, 0, 0),
        Seq(0, 0, 1, 0),
        Seq(0, 0, 0, 1)
      )
      val b = Seq(
        Seq(1, 2, 3, 4),
        Seq(5, 6, 7, 8),
        Seq(9, 10, 11, 12),
        Seq(13, 14, 15, 16)
      )
      loadMatrixA(dut, a)
      loadMatrixB(dut, b)
      runOp(dut, Opcodes.MATMUL_LIT, limit = 128)
      expectMatrixC(dut, b)

      // Non-trivial: A = [[1,1,0,0],...] row sums of first two of B
      runOp(dut, Opcodes.CLEAR_LIT)
      loadMatrixA(
        dut,
        Seq(
          Seq(1, 1, 0, 0),
          Seq(0, 0, 1, 1),
          Seq(1, 0, 1, 0),
          Seq(0, 1, 0, 1)
        )
      )
      loadMatrixB(dut, b)
      runOp(dut, Opcodes.MATMUL_LIT, limit = 128)

      def dot(row: Seq[Int], colIdx: Int): Int =
        row.zipWithIndex.map { case (v, k) => v * b(k)(colIdx) }.sum

      val aa = Seq(
        Seq(1, 1, 0, 0),
        Seq(0, 0, 1, 1),
        Seq(1, 0, 1, 0),
        Seq(0, 1, 0, 1)
      )
      expectMatrixC(
        dut,
        for (r <- 0 until 4) yield for (c <- 0 until 4) yield dot(aa(r), c)
      )
    }
  }

  it should "pulse busy then done for single-cycle ops" in {
    test(new MatrixCore) { dut =>
      idle(dut)
      dut.io.busy.expect(false.B)
      issue(dut, Opcodes.WR_A_LIT, 0, 0, 42)
      // After issue step, busy should be high entering execute
      dut.io.busy.expect(true.B)
      dut.clock.step(1)
      dut.io.done.expect(true.B)
      dut.io.busy.expect(false.B)
      dut.clock.step(1)
      dut.io.done.expect(false.B)
    }
  }

  it should "ignore unknown opcodes by finishing immediately" in {
    test(new MatrixCore) { dut =>
      idle(dut)
      issue(dut, 0xf)
      waitDone(dut, 4)
      dut.io.busy.expect(false.B)
    }
  }
}
