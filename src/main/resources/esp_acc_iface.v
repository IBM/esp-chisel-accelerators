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

module sort_basic_dma32
  (
   clk,
   rst,
   conf_info_len,
   conf_info_batch,
   conf_done,
   acc_done,
   debug,
   dma_read_chnl_valid,
   dma_read_chnl_data,
   dma_read_chnl_ready,
   dma_read_ctrl_valid,
   dma_read_ctrl_data_index,
   dma_read_ctrl_data_length,
   dma_read_ctrl_ready,
   dma_write_ctrl_valid,
   dma_write_ctrl_data_index,
   dma_write_ctrl_data_length,
   dma_write_ctrl_ready,
   dma_write_chnl_valid,
   dma_write_chnl_data,
   dma_write_chnl_ready
   );

   input clk;
   input rst;

   // Configuration input (assigned from memory-mapped registers in
   // the tile. There can be up to 14 32-bits user-defined registers.
   // We've reserved registers 15 and 16 to control a small on-tile
   // memory in case more memory-mapped registers are needed.
   input [31:0] conf_info_len;
   input [31:0] conf_info_batch;

   // Start accelerator (assigned from memory-mapped command register
   // in the tile
   input 	conf_done;

   // DMA Read control
   input 	dma_read_ctrl_ready;
   output 	dma_read_ctrl_valid;
   reg 		dma_read_ctrl_valid;
   // Offset within contiguous accelerator virtual address (gets
   // translated by TLB in the tile)
   output [31:0] dma_read_ctrl_data_index;
   reg [31:0] 	 dma_read_ctrl_data_index;
   // Number of 32-bit words to be read
   // This can be converted to number of Bytes, but it was convenient
   // since we design accelerators in SytemC
   output [31:0] dma_read_ctrl_data_length;
   reg [31:0] 	 dma_read_ctrl_data_length;

   // DMA Read data channel directly connected to the NoC queues.
   output 	 dma_read_chnl_ready;
   input 	 dma_read_chnl_valid;
   input [31:0]  dma_read_chnl_data;

   // DMA Write control (same as Read)
   input 	 dma_write_ctrl_ready;
   output 	 dma_write_ctrl_valid;
   reg 		 dma_write_ctrl_valid;
   output [31:0] dma_write_ctrl_data_index;
   reg [31:0] 	 dma_write_ctrl_data_index;
   output [31:0] dma_write_ctrl_data_length;
   reg [31:0] 	 dma_write_ctrl_data_length;

   // DMA Write data channel directly connected to the NoC queues
   input 	 dma_write_chnl_ready;
   output 	 dma_write_chnl_valid;
   output [31:0] dma_write_chnl_data;
   reg [31:0] 	 dma_write_chnl_data;

   // Latency-insensitive protocol.
   // Note that read/valid may or may not be marked as "reg"
   // depending on which latency-incensitive channel was chosen
   // for HLS. Some are blocking, some are may-be-blocking.
   // Regardless, the protocol is simple: when both ready and
   // valid are set, the producer knows that data have been
   // consumed. There is no combinational loop between ready
   // and valid, because accelerators are implemented using
   // SC_CTHREADS only. Therefore, inputs are implicitly
   // registered.

   // one-cycle pulse (triggers the interrupt from the tile.
   // Interrupts are routed through the NoC to the tile that
   // hosts the interrupt controller.
   output 	acc_done;
   reg 		acc_done;

   // Optional debug port to set an error code.
   // Currently we are not using this though.
   output [31:0] debug;


   ///.. HLS-generated code
endmodule
