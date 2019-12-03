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

package esp.examples

import chisel3._
import chisel3.util.Valid
import chisel3.experimental.withReset

class ShiftArrayIO[A <: Data](rows: Int, cols: Int, gen: A) extends Bundle {

  val in = Flipped(Valid(Vec(rows, gen.cloneType)))
  val out = Valid(Vec(rows, Vec(cols, gen.cloneType)))

}

class ShiftArray[A <: Data](val rows: Int, val cols: Int, gen: A) extends Module {

  val io = IO(new ShiftArrayIO(rows, cols, gen))

  val regArray = Seq.fill(rows)(Seq.fill(cols)(Reg(gen)))
  val count = RegInit(0.U(cols.W))

  /* Shift regArray left when the input fires */
  when (io.in.fire()) {
    count := count ## 1.U
    regArray
      .zip(io.in.bits)
      .foreach{ case (a, in) => a.foldLeft(in){ case (r, l) => l := r; l } }
  }

  /* Route the regArray to the output */
  io.out.bits.flatten
    .zip(regArray.flatten)
    .foreach{ case (l, r) => l := r }

  io.out.valid := count.toBools.last

}
