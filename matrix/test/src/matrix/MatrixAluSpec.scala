package matrix

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class MatrixAluSpec extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "MatrixAlu"

  it should "compute unsigned add, sub, product and MAC" in {
    test(new MatrixAlu) { dut =>
      dut.io.a.poke(3.U)
      dut.io.b.poke(5.U)
      dut.io.c.poke(10.U)
      dut.io.product.expect(15.U)
      dut.io.madd.expect(8.U)
      dut.io.msub.expect(BigInt("fffffffe", 16).U) // 3 - 5 as 32-bit wrap
      dut.io.hmul.expect(15.U)
      dut.io.mac.expect(25.U)

      dut.io.a.poke(255.U)
      dut.io.b.poke(255.U)
      dut.io.c.poke(1.U)
      dut.io.product.expect(65025.U)
      dut.io.hmul.expect(65025.U)
      dut.io.mac.expect(65026.U)
      dut.io.madd.expect(510.U)
    }
  }
}
