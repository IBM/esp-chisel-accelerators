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

// The module TileFetch fetches a tile or window of pixels per cycle using a set of line buffers and shift registers
// parameters - wSize = size of window of pixels held in shift registers (3x3)
//              bufferDepth = depth of the line buffer, there are wSize+1 such line buffers
//              numBuffers = wSize+1
//              dWidth = data width of each element in the line buffer and shift register

package esp.examples

import chisel3._
import chisel3.util._

class TileFetch (wSize: Int, bufferDepth: Int, numBuffers: Int, dWidth: Int) extends Module {
  val io = IO(new Bundle {
    val datain           = Input(Valid(UInt(dWidth.W)))
    val wdataout         = Output(Vec(wSize, Vec(wSize, UInt(dWidth.W))))
    val wdataoutValid   = Output(Bool())
  })

  io.wdataout.map(_ := DontCare)
  io.wdataoutValid := DontCare

  // Count incoming data and keep track of it as modulo bufferDepth
  val datain_ctr = RegInit(UInt(32.W), 0.U)
  when (io.datain.valid) {
    printf(p"[info] datain valid: ${io.datain.bits}\n")
    when (datain_ctr === (bufferDepth - 1).U) {
      datain_ctr := 0.U(32.W)
    }.otherwise {
      datain_ctr := datain_ctr + 1.U
    }
  }

  // There is an initial fill latency, there after data fills and flows in appropriate line buffers
  val fillBuffers = RegInit(UInt(numBuffers.W),1.U)
  val flowBuffers = Wire(UInt(numBuffers.W))
  val s_fill :: s_fill_flow :: Nil = Enum(2)
  val state = RegInit(s_fill)
  val fill_ctr = RegInit(UInt(log2Up(wSize).W), 0.U)

  switch (state) {
    is (s_fill) {

      when (datain_ctr === (bufferDepth - 1).U) {
        fill_ctr := fill_ctr + 1.U
        fillBuffers := fillBuffers << 1
      }

      when ((datain_ctr === (bufferDepth - 1).U) && (fill_ctr === (wSize - 1).U)) {
        state := s_fill_flow
      }

    }
    is (s_fill_flow) {
      when (fill_ctr === wSize.U){
        fill_ctr := 0.U
      }
      when (datain_ctr === (bufferDepth - 1).U) {
        fillBuffers := fillBuffers << 1
      }
    }
  }
  when (state === s_fill) {
    flowBuffers := 0.U
  }.otherwise {
    flowBuffers := ~fillBuffers
  }


  // An Array of line buffers
  //

  val dataout_to_shift = Wire(Vec(numBuffers, UInt(numBuffers.W)))
  val LineBuffers   = Array.fill(numBuffers)(Module(new LineBuffer(bufferDepth, dWidth)).io)
  for (l <- 0 until (numBuffers)) {
    LineBuffers(l).rdEn         := flowBuffers(l)
    LineBuffers(l).wrEn         := fillBuffers(l)
    LineBuffers(l).wrData       := io.datain.bits(l)
    dataout_to_shift(l)         := LineBuffers(l).dataOut
  }
}



class LineBuffer (depth: Int, dwidth: Int) extends Module {
  val io = IO(new Bundle {
    val wrData          = Input(UInt(dwidth.W))
    val wrEn            = Input(Bool())
    val rdEn            = Input(Bool())
    val dataOut         = Output(UInt(dwidth.W))
    val dataOutValid     = Output(Bool())
  })
  val awidth           = log2Up(dwidth)
  val wr_addr          = RegInit(UInt(awidth.W), 0.U) // check how to write log(depth)
  val rd_addr          = RegInit(UInt(awidth.W), 0.U) // check how to write log(depth)

  val mem = SyncReadMem(depth, UInt(dwidth.W))

  when (io.wrEn) {
    mem.write(wr_addr, io.wrData)
    when (wr_addr === depth.U-1.U) {
      wr_addr := 0.U
    }.otherwise {
      wr_addr       := wr_addr + 1.U
    }
  }
  when (io.rdEn) {
    io.dataOut := mem.read(rd_addr)
    io.dataOutValid := true.B
    when (rd_addr === depth.U-1.U) {
      rd_addr := 0.U
    }.otherwise {
      rd_addr       := rd_addr + 1.U
    }
  }.otherwise {
    io.dataOut := 0.U
    io.dataOutValid := false.B
  }
}

class ShiftRegister (wSize: Int, dWidth: Int) extends Module {
  val io = IO(new Bundle {
    val in  = Input(UInt(dWidth.W))
    val out = Output(Vec(wSize,UInt(dWidth.W)))
  })
  val shiftReg = Reg(Vec(wSize, UInt(dWidth.W)))  // An array of registers in hardware
  for (i <- 0 until wSize-1) {
    shiftReg(i+1) := shiftReg(i)
  }

  for (i <- 0 until wSize) {
    io.out(i) := shiftReg(i)
  }
//  val r0 = RegNext(io.in)
//  val r1 = RegNext(r0)
//  val r2 = RegNext(r1)
//  val r3 = RegNext(r2)
//  io.out := r3
}
