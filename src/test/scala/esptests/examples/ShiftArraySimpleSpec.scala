// Copyright 2019 IBM
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package esptests.examples

import chisel3._
import chisel3.iotesters.{ChiselFlatSpec, Driver, AdvTester}

import esp.examples.ShiftArraySimple

class ShiftArraySimpleTester[A <: Bits](dut: ShiftArraySimple[A]) extends AdvTester(dut) {
  def init(): Unit = {
    Seq(dut.io.in.valid).map(p => wire_poke(p, false))
  }

  def load(a: Seq[Int], valid: Seq[(Int, Int)]): Unit = {
    require(a.size == valid.size)
    wire_poke(dut.io.in.valid, true)
    a.zip(valid).map{ case (aa, v) =>
      expect(peek(dut.io.out.valid) == v._1, s"dut.io.out.valid was NOT ${v._1} (was ${peek(dut.io.out.valid)})")
      wire_poke(dut.io.in.bits, aa)
      step(1)
      expect(peek(dut.io.out.valid) == v._2, s"dut.io.out.valid was NOT ${v._2} (was ${peek(dut.io.out.valid)})")
    }
    wire_poke(dut.io.in.valid, false)
  }

  reset(4)

  init()
  step(1)

  load(0 until 9, Seq.fill(8)((0, 0)) :+ (0, 1))
  peek(dut.io.out.bits).zip(8 to 0 by -1).map{ case (out, expected) => expect(out == expected, s"$out != $expected") }
  println(peek(dut.io.out.bits).mkString(", "))

  load(9 until 12, Seq((1, 0), (0, 0), (0, 1)))
  peek(dut.io.out.bits).zip(11 to 3 by -1).map{ case (out, expected) => expect(out == expected, s"$out != $expected") }
  println(peek(dut.io.out.bits).mkString(", "))

  load(12 until 15, Seq((1, 0), (0, 0), (0, 1)))
  peek(dut.io.out.bits).zip(14 to 6 by -1).map{ case (out, expected) => expect(out == expected, s"$out != $expected") }
  println(peek(dut.io.out.bits).mkString(", "))

  reset(1)
  load(15 until (15 + 9), Seq.fill(8)((0, 0)) :+ (0, 1))
  peek(dut.io.out.bits).zip(23 to 14 by -1).map{ case (out, expected) => expect(out == expected, s"$out != $expected") }
  println(peek(dut.io.out.bits).mkString(", "))

  load((15 + 9) until (15 + 9 + 3), Seq((1, 0), (0, 0), (0, 1)))
  peek(dut.io.out.bits).zip(26 to 17 by -1).map{ case (out, expected) => expect(out == expected, s"$out != $expected") }
  println(peek(dut.io.out.bits).mkString(", "))
}

class ShiftArraySimpleSpec extends ChiselFlatSpec {

  behavior of "ShiftArraySimple"

  it should "work" in {
    Driver(() => new ShiftArraySimple(3, 3, UInt(16.W)), "verilator") {
      dut => new ShiftArraySimpleTester(dut)
    } should be (true)
  }

}
