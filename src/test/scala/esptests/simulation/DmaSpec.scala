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
import chisel3.iotesters.{ChiselFlatSpec, Driver, AdvTester}

import java.io.File

import esp.simulation.Dma

import scala.collection.mutable

class DmaTester[A <: Data](dut: Dma[A], delay: Option[Int]) extends AdvTester(dut) {

  /** Reset the inputs to some known-safe state */
  protected def reset(): Unit =
    Seq( dut.io.readControl.valid,
         dut.io.writeControl.valid,
         dut.io.readChannel.ready,
         dut.io.writeChannel.valid )
      .map( wire_poke(_, false.B) )

  /** Read some data from the simulation memory
    * @param addr base address
    * @param length number of words to read
    * @param delay optional delay between consecutive reads
    * @return a sequence of values read
    */
  protected def read(addr: Int, length: Int, delay: Option[Int]): Seq[BigInt] = {
    /* Before starting, there should be nothing on the read channel */
    expect(dut.io.readChannel.valid, false)

    /* Output data written to a mutable buffer */
    val data = mutable.ListBuffer[BigInt]()

    /* Assert the read request */
    wire_poke(dut.io.readControl.valid, true.B)
    wire_poke(dut.io.readControl.bits.index, addr.U)
    wire_poke(dut.io.readControl.bits.length, length.U)

    /* Wait until readControl is ready */
    eventually(peek(dut.io.readControl.ready) == 1)

    step(1)

    reset()

    /* Wait until all expected things are read */
    for (i <- 0 until length) {
      eventually(peek(dut.io.readChannel.valid) == 1)

      wire_poke(dut.io.readChannel.ready, true.B)
      peek(dut.io.readChannel.bits) +=: data
      step(1)
      reset()

      delay.map { case d =>
        wire_poke(dut.io.readChannel.ready, false.B)
        step(d)
      }
    }

    reset()
    step(1)

    data.reverse
  }

  /**
    *
    */
  protected def write(addr: Int, data: Seq[Int], delay: Option[Int]): Unit = {
    /* Before starting, the writeChannel should act like it can't accept data */
    expect(dut.io.writeChannel.ready, false)

    /* Assert the write request */
    wire_poke(dut.io.writeControl.valid, true)
    wire_poke(dut.io.writeControl.bits.index, addr)
    wire_poke(dut.io.writeControl.bits.length, data.size)

    /* Wait until writeControl is ready */
    eventually(peek(dut.io.writeControl.ready) == 1)

    step(1)
    reset()

    /* Wait until all data is written */
    data.map { case word =>
      eventually(peek(dut.io.writeChannel.ready) == 1)

      wire_poke(dut.io.writeChannel.valid, true)
      wire_poke(dut.io.writeChannel.bits, word)
      step(1)
      reset()

      delay.map { case d =>
        wire_poke(dut.io.writeChannel.ready, false)
        step(d)
      }
    }

  }

}

class DmaReadTester[A <: Data](dut: Dma[A], delay: Option[Int]) extends DmaTester(dut, delay) {

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

class DmaWriteTester[A <: Data](dut: Dma[A], delay: Option[Int]) extends DmaTester(dut, delay) {

  step(1)
  reset()

  val tests: Seq[(Int, Seq[Int])] = Seq(
    (0, Seq(10)),
    (0, Seq(20, 21)),
    (8, 30.until(38)),
    (0, 40.until(40 + 16)),
    (31, Seq.empty[Int]) )

  tests
    .map{ case (addr, data) =>
      println(s"""Write mem[$addr]: ${data.mkString(", ")}""")
      write(addr, data, delay)
      val out: Seq[BigInt] = read(addr, data.size, None)
      println(s"""Read mem[$addr+:${data.size}]: ${out.mkString(", ")}""")
      assert(out == data, s"Read '$out' did not match written '$data'")
    }

}

class DmaSpec extends ChiselFlatSpec {

  val resourceDir: File = new File(System.getProperty("user.dir"), "src/test/resources")

  behavior of classOf[esp.simulation.Dma[UInt]].getName

  it should "Read from memory without delays" in {

    Driver(() => new Dma(1024, UInt(8.W), Some(new File(resourceDir, "linear-mem.txt").toString)), "treadle") {
      dut => new DmaReadTester(dut, None)
    } should be (true)

  }

  it should "Read from memory with delays between reads" in {

    Driver(() => new Dma(1024, UInt(8.W), Some(new File(resourceDir, "linear-mem.txt").toString)), "treadle") {
      dut => new DmaReadTester(dut, Some(16))
    } should be (true)

  }

  it should "Write to memory without delays" in {

    Driver(() => new Dma(1024, UInt(8.W), Some(new File(resourceDir, "linear-mem.txt").toString)), "treadle") {
      dut => new DmaWriteTester(dut, None)
    } should be (true)

  }

  it should "Write to memory with delays between writes" in {

    Driver(() => new Dma(1024, UInt(8.W), Some(new File(resourceDir, "linear-mem.txt").toString)), "treadle") {
      dut => new DmaWriteTester(dut, Some(16))
    } should be (true)

  }

}
