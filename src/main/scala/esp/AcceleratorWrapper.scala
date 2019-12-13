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

package esp

import chisel3._

trait AcceleratorWrapperIO { this: RawModule =>
  val dmaWidth: Int

  val clk = IO(Input(Clock()))
  val rst = IO(Input(Bool()))

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
  val dma_read_chnl_data = IO(Input(UInt(dmaWidth.W)))

  // DMA Read control
  val dma_read_ctrl_ready = IO(Input(Bool()))
  val dma_read_ctrl_valid = IO(Output(Bool()))
  // Offset within contiguous accelerator virtual address (gets translated by TLB in the tile)
  val dma_read_ctrl_data_index = IO(Output(UInt(32.W)))
  // Number of 32-bit words to be read This can be converted to number of Bytes, but it was convenient since we design
  // accelerators in SytemC
  val dma_read_ctrl_data_length = IO(Output(UInt(32.W)))
  // Size of the data tokens encoded according to AXI bus standard (e.g. double-word, word, half-word, byte).
  val dma_read_ctrl_data_size = IO(Output(DmaSize.gen))

  // DMA Write control (same as Read)
  val dma_write_ctrl_ready = IO(Input(Bool()))
  val dma_write_ctrl_valid = IO(Output(Bool()))
  val dma_write_ctrl_data_index = IO(Output(UInt(32.W)))
  val dma_write_ctrl_data_length = IO(Output(UInt(32.W)))
  val dma_write_ctrl_data_size = IO(Output(DmaSize.gen))

  // DMA Write data channel directly connected to the NoC queues
  val dma_write_chnl_ready = IO(Input(Bool()))
  val dma_write_chnl_valid = IO(Output(Bool()))
  val dma_write_chnl_data = IO(Output(UInt(dmaWidth.W)))
}

/** Wraps a given [[Accelerator]] in a predicatable top-level interface. This is intended for direct integration with
  * the ESP acclerator socket.
  * @param gen the accelerator to wrap
  * accelerator from anoter
  */
final class AcceleratorWrapper(val dmaWidth: Int, gen: Int => Implementation) extends RawModule with AcceleratorWrapperIO {

  override lazy val desiredName = s"${acc.config.name}_${acc.implementationName}_dma$dmaWidth"
  val acc = withClockAndReset(clk, ~rst)(Module(gen(dmaWidth)))

  val conf_info = acc.io.config.map(a => IO(Input(a.cloneType)))

  if (conf_info.isDefined) {
    acc.io.config.get           := conf_info.get
  }

  acc.io.enable                 := conf_done

  acc_done                      := acc.io.done

  debug                         := acc.io.debug

  acc.io.dma.readControl.ready  := dma_read_ctrl_ready
  dma_read_ctrl_valid           := acc.io.dma.readControl.valid
  dma_read_ctrl_data_index      := acc.io.dma.readControl.bits.index
  dma_read_ctrl_data_length     := acc.io.dma.readControl.bits.length
  dma_read_ctrl_data_size       := acc.io.dma.readControl.bits.size

  acc.io.dma.writeControl.ready := dma_write_ctrl_ready
  dma_write_ctrl_valid          := acc.io.dma.writeControl.valid
  dma_write_ctrl_data_index     := acc.io.dma.writeControl.bits.index
  dma_write_ctrl_data_length    := acc.io.dma.writeControl.bits.length
  dma_write_ctrl_data_size      := acc.io.dma.writeControl.bits.size

  dma_read_chnl_ready           := acc.io.dma.readChannel.ready
  acc.io.dma.readChannel.valid  := dma_read_chnl_valid
  acc.io.dma.readChannel.bits   := dma_read_chnl_data

  acc.io.dma.writeChannel.ready := dma_write_chnl_ready
  dma_write_chnl_valid          := acc.io.dma.writeChannel.valid
  dma_write_chnl_data           := acc.io.dma.writeChannel.bits
}
