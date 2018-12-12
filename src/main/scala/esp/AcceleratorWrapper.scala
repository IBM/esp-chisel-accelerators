// Copyright 2018 IBM
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

package esp

import chisel3._
import chisel3.experimental.{RawModule, withClockAndReset}

trait AcceleratorWrapperIO { this: RawModule =>
  val clk = IO(Input(Clock()))
  val rst = IO(Input(Bool()))

  // Configuration input (assigned from memory-mapped registers in the tile. There can be up to 14 32-bits user-defined
  // registers. We've reserved registers 15 and 16 to control a small on-tile memory in case more memory-mapped
  // registers are needed.
  val conf_info_len = IO(Input(UInt(32.W)))
  val conf_info_batch = IO(Input(UInt(32.W)))

  // Start accelerator (assigned from memory-mapped command register in the tile
  val conf_done = IO(Input(Bool()))

  // One-cycle pulse (triggers the interrupt from the tile). Interrupts are routed through the NoC to the tile that
  // hosts the interrupt controller.
  val acc_done = IO(Output(Bool()))

  // Optional debug port to set an error code. Currently we are not using this though.
  val debug = IO(Output(UInt(32.W)))

  // DMA Read data channel directly connected to the NoC queues.
  val dma_read_chnl_ready = IO(Output(Bool()))
  val dma_read_chnl_valid = IO(Input(Bool()))
  val dma_read_chnl_data = IO(Input(UInt(32.W)))

  // DMA Read control
  val dma_read_ctrl_ready = IO(Input(Bool()))
  val dma_read_ctrl_valid = IO(Output(Bool()))
  // Offset within contiguous accelerator virtual address (gets translated by TLB in the tile)
  val dma_read_ctrl_data_index = IO(Output(UInt(32.W)))
  // Number of 32-bit words to be read This can be converted to number of Bytes, but it was convenient since we design
  // accelerators in SytemC
  val dma_read_ctrl_data_length = IO(Output(UInt(32.W)))

  // DMA Write control (same as Read)
  val dma_write_ctrl_ready = IO(Input(Bool()))
  val dma_write_ctrl_valid = IO(Output(Bool()))
  val dma_write_ctrl_data_index = IO(Output(UInt(32.W)))
  val dma_write_ctrl_data_length = IO(Output(UInt(32.W)))

  // DMA Write data channel directly connected to the NoC queues
  val dma_write_chnl_ready = IO(Input(Bool()))
  val dma_write_chnl_valid = IO(Output(Bool()))
  val dma_write_chnl_data = IO(Output(UInt(32.W)))
}

/** Wraps a given [[Accelerator]] in a predicatable top-level interface. This is intended for direct integration with
  * the ESP acclerator socket.
  * @param gen the accelerator to wrap
  * @param subName the top-level "name" of the accelerator
  * @param parameters a string, typically consiting of stringified parameters, used to disambiguate this instance of the
  * accelerator from anoter
  * @todo Make subName and parameters automatically inferred based on gen. This requires some merged support added to
  * Chisel.
  */
class AcceleratorWrapper(gen: => Accelerator, subName: String, parameters: String) extends RawModule
    with AcceleratorWrapperIO {

  override lazy val desiredName = s"${subName}_${parameters}_Wrapper"
  val acc = withClockAndReset(clk, rst)(Module(gen))

  acc.io.conf.bits.length       := conf_info_len
  acc.io.conf.bits.batch        := conf_info_batch
  acc.io.conf.valid             := conf_done

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
