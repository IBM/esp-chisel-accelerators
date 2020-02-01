// Copyright 2018-2019 IBM
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
import chisel3.experimental.BundleLiterals._
import chisel3.tester._

import org.scalatest.{FlatSpec, Matchers}

import esp.{DmaControl, DmaSize}
import esp.examples.AdderAccelerator

import esptests.AcceleratorSpec._

class AdderAcceleratorSpec extends FlatSpec with ChiselScalatestTester with Matchers {

  behavior of "AdderAccelerator"

  private def adderTest(input: Seq[Int], readAddr: Int = 0, writeAddr: Int = 0) = {
    val expectedOutput = input.foldLeft(0){ case (acc, x) => acc + x }
    it should s"""reduce [${input.mkString(",")}] to ${expectedOutput}""" in {
      test(new AdderAccelerator(32)) { dut =>

        dut.doReset()

        dut.io.dma.readControl.initSink().setSinkClock(dut.clock)
        dut.io.dma.readChannel.initSource().setSourceClock(dut.clock)
        dut.io.dma.writeControl.initSink().setSinkClock(dut.clock)
        dut.io.dma.writeChannel.initSink().setSinkClock(dut.clock)

        timescope {
          dut.io.config.get("readAddr").poke(readAddr.U)
          dut.io.config.get("size").poke(input.length.U)
          dut.io.config.get("writeAddr").poke(writeAddr.U)
          dut.io.enable.poke(true.B)
          dut.clock.step()
        }

        input.length match {
          case 0 =>
          case _ =>
            dut.io.dma.readControl
              .expectDequeue((new DmaControl).Lit(_.index -> readAddr.U, _.length -> input.length.U, _.size -> DmaSize.word))
            dut.io.dma.readChannel.enqueueSeq(input.map(_.U))
        }

        dut.io.dma.writeControl
          .expectDequeue((new DmaControl).Lit(_.index -> writeAddr.U, _.length -> 1.U, _.size -> DmaSize.word))

        dut.io.dma.writeChannel
          .expectDequeue(input.foldLeft(0){ case (acc, x) => acc + x }.U)

        dut.io.done.expect(true.B)

      }
    }

  }

  Seq( Seq.empty[Int],
       Seq(0),
       Seq(1),
       Seq(1, 2, 3),
       Seq(100, 200, 300, 400, 500, 600, 700, 800, 900, 1000))
    .foreach(adderTest(_))

}
