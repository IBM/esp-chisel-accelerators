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

package esptests.simulation

import chisel3._
import chisel3.iotesters.{ChiselFlatSpec, Driver, PeekPokeTester}

import java.io.File

import esp.simulation.Dma

import scala.collection.mutable

class DmaTester[A <: Data](dut: Dma[A], delay: Option[Int]) extends PeekPokeTester(dut) {

  def reset(): Unit =
    Seq( dut.io.readControl.valid,
         dut.io.writeControl.valid,
         dut.io.readChannel.ready,
         dut.io.writeChannel.ready )
      .map( poke(_, false.B) )

  def read(addr: Int, length: Int, delay: Option[Int]): Seq[BigInt] = {
    /* Before starting, there should be nothing on the read channel */
    expect(dut.io.readChannel.valid, false)

    /* Output data written to a mutable buffer */
    val data = mutable.ListBuffer[BigInt]()

    /* Wait until readControl is ready */
    while( peek(dut.io.readControl.ready) == 0) { step(1) }

    /* Assert the read */
    poke(dut.io.readControl.valid, true.B)
    poke(dut.io.readControl.bits.index, addr.U)
    poke(dut.io.readControl.bits.length, length.U)
    step(1)

    reset()

    /* Wait until all expected things are read */
    for (i <- 0 until length) {
      while (peek(dut.io.readChannel.valid) == 0) { step(1) }

      poke(dut.io.readChannel.ready, true.B)
      peek(dut.io.readChannel.bits) +=: data
      step(1)

      delay.map { case d =>
        poke(dut.io.readChannel.ready, false.B)
        step(d)
        poke(dut.io.readChannel.ready, true.B)
      }
    }

    reset()
    step(1)

    data.reverse
  }

  step(1)
  reset()

  Seq( (0, 1, Seq(0)),
       (0, 2, Seq(0, 1)),
       (8, 8, 8.until(16)),
       (0, 16, 0.until(16)),
       (31, 0, Seq.empty) )
    .map{ case (addr, length, expected) =>
      val out: Seq[BigInt] = read(addr, length, delay)
      assert(out == expected, s"Read sequence '$out', expected '$expected'")
      println(s"""Read mem[$addr+:$length]: ${out.mkString(", ")}""")
    }

}

class DmaSpec extends ChiselFlatSpec {

  val resourceDir: File = new File(System.getProperty("user.dir"), "src/test/resources")

  behavior of classOf[esp.simulation.Dma[UInt]].getName

  it should "Read from memory without delays" in {

    Driver(() => new Dma(1024, UInt(8.W), Some(new File(resourceDir, "linear-mem.txt").toString)), "treadle") {
      dut => new DmaTester(dut, None)
    } should be (true)

  }

  it should "Read from memory with delays between reads" in {

    Driver(() => new Dma(1024, UInt(8.W), Some(new File(resourceDir, "linear-mem.txt").toString)), "treadle") {
      dut => new DmaTester(dut, Some(16))
    } should be (true)

  }

  it should "Write to memory without delays" in (pending)

  it should "Write to memory with delays between writes" in (pending)

}
