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

import esp.examples.ShiftArray

class ShiftArrayTester[A <: Bits](dut: ShiftArray[A]) extends AdvTester(dut) {
  def init(): Unit = {
    Seq(dut.io.in.valid).map(p => wire_poke(p, false))
  }

  def load(in: Seq[Int], outValid: Boolean): Unit = {
    require(in.size % dut.rows == 0)
    in
      .grouped(dut.rows)
      .foreach{ case a =>
        expect(dut.io.out.valid, outValid)
        wire_poke(dut.io.in.valid, 1)
        a.zipWithIndex.map{ case (b, i) => wire_poke(dut.io.in.bits(i), b) }
        step(1)
        wire_poke(dut.io.in.valid, 0) }
  }

  def compare(in: Seq[Int]): Unit = {
    expect(dut.io.out.valid, true)
    val out: Seq[Int] = peek(dut.io.out.bits).map(_.toInt)
    val expected: Seq[Int] = in
      .grouped(dut.rows).toSeq
      .transpose
      .map(_.reverse)
      .flatten
    println(s"""read: ${out.mkString(", ")}""")
    out.zip(expected).map{ case (o, e) => expect(o == e, s"($o should be $e)") }
  }

  val input: Seq[Int] = 0 until dut.rows * dut.cols

  reset(4)
  expect(dut.io.out.valid, false)

  init()
  step(1)

  load(0 until 9, false)
  compare(0 until 9)

  load(9 until 12, true)
  compare(3 until 12)

  reset(1)
  expect(dut.io.out.valid, false)

  load(12 until 21, false)
  compare(12 until 21)
}

class ShiftArraySpec extends ChiselFlatSpec {

  behavior of "ShiftArray"

  it should "present a 3x3 array" in {
    Driver(() => new ShiftArray(3, 3, UInt(16.W)), "treadle") {
      dut => new ShiftArrayTester(dut)
    } should be (true)
  }

}
