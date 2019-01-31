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

package esp.examples

import chisel3._
import chisel3.experimental.{RawModule, withClockAndReset}

import esp.{Config, AcceleratorWrapperIO, AcceleratorIO, Implementation, Parameter, Specification}

import sys.process._

/** An ESP accelerator that is done a parameterized number of clock ticks in the future
  * @param ticks the number of clock ticks until done
  */
trait CounterSpecification extends Specification {

  /* This defines the abstract member config that provides necessary information for the ESP framework to generate an XML
   * accelerator configuration. At the Chisel level, this will be used to emit an [[esp.EspConfigAnnotation]] which will
   * be converted to an XML description by a custom FIRRTL transform, [[esp.transforms.EmitXML]]. */
  override lazy val config: Config = Config(
    name = "CounterAccelerator",
    description = s"Fixed-count timer",
    memoryFootprintMiB = 0,
    deviceId = 0xC,
    param = Array(
      Parameter(
        name = "gitHash",
        description = Some("Git short SHA hash of the repo used to generate this accelerator"),
        value = Some(Integer.parseInt(("git log -n1 --format=%h" !!).filter(_ >= ' '), 16))
      ),
      Parameter(
        name = "ticks",
        description = Some("Ticks to timeout"),
        value = None)
    )
  )

}

class CounterAcceleratorIO(dmaWidth: Int) extends AcceleratorIO(dmaWidth) { self: CounterAcceleratorIO =>
  val conf_info_ticks = Input(UInt(32.W))
}

class CounterAcceleratorImplementation(dmaWidth: Int) extends Implementation(dmaWidth) with CounterSpecification {

  override val implementationName: String = "Default_dma" + dmaWidth

  final lazy val io = IO(new CounterAcceleratorIO(dmaWidth))

  InitCommonIo(io)
}

class CounterAccelerator(dmaWidth: Int) extends CounterAcceleratorImplementation(dmaWidth) {

  val ticks = RegInit(42.U)

  val enabled = RegInit(false.B)
  val done    = RegInit(false.B)
  val value   = RegInit(0.U(16.W))

  when (io.enable)         { enabled := true.B; ticks := io.conf_info_ticks - 1.U}
  when (enabled & ~done)   { value := value + 1.U }
  when (value === ticks)   { done := true.B }
  when (~io.enable)        { enabled := false.B; done := false.B }

  io.done := done
}

/** Wraps CounterAccelerator in a predicatable top-level interface. This is intended for direct integration with
  * the ESP acclerator socket.
  * @param gen is the accelerator implementation to wrap
  */
class CounterAcceleratorWrapper(val dmaWidth: Int, gen: Int => CounterAcceleratorImplementation) extends RawModule with AcceleratorWrapperIO {
  override lazy val desiredName = s"${acc.config.name}_${acc.implementationName}"
  val acc = withClockAndReset(clk, ~rst)(Module(gen(dmaWidth)))

  val conf_info_ticks = IO(Input(UInt(32.W)))

  acc.io.conf_info_ticks        := conf_info_ticks

  acc.io.conf_info_ticks        := conf_info_ticks

  acc.io.enable                 := conf_done

  acc_done                      := acc.io.done

  debug                         := acc.io.debug

  acc.io.dma.readControl.ready  := dma_read_ctrl_ready
  dma_read_ctrl_valid           := acc.io.dma.readControl.valid
  dma_read_ctrl_data_index      := acc.io.dma.readControl.bits.index
  dma_read_ctrl_data_length     := acc.io.dma.readControl.bits.length

  acc.io.dma.writeControl.ready := dma_write_ctrl_ready
  dma_write_ctrl_valid          := acc.io.dma.writeControl.valid
  dma_write_ctrl_data_index     := acc.io.dma.writeControl.bits.index
  dma_write_ctrl_data_length    := acc.io.dma.writeControl.bits.length

  dma_read_chnl_ready           := acc.io.dma.readChannel.ready
  acc.io.dma.readChannel.valid  := dma_read_chnl_valid
  acc.io.dma.readChannel.bits   := dma_read_chnl_data

  acc.io.dma.writeChannel.ready := dma_write_chnl_ready
  dma_write_chnl_valid          := acc.io.dma.writeChannel.valid
  dma_write_chnl_data           := acc.io.dma.writeChannel.bits
}
