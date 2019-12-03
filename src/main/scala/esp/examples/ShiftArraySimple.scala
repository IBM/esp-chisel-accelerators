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
import chisel3.util.{log2Up, Valid}

class ShiftArraySimpleIO[A <: Data](rows: Int, cols: Int, gen: A) extends Bundle {

  val in = Flipped(Valid(gen.cloneType))
  val out = Valid(Vec(rows * cols, gen.cloneType))

}

class ShiftArraySimple[A <: Data](rows: Int, cols: Int, gen: A) extends Module {

  val io = IO(new ShiftArraySimpleIO(rows, cols, gen))

  val reg: Seq[A] = Seq.fill(rows * cols)(Reg(gen.cloneType))

  val fullMask: UInt = RegInit(0.U((rows * cols).W))

  val counterValid: UInt = RegInit(0.U(rows.W))

  when (io.in.fire()) {
    reg.foldLeft(io.in.bits){ case (r, l) => l := r; l }
    fullMask := fullMask ## 1.U
    counterValid := Mux(counterValid === (rows - 1).U, 0.U, counterValid + 1.U)
  }

  io.out.valid := (fullMask.toBools.last) && (counterValid === 0.U)
  io.out.bits := reg

}
