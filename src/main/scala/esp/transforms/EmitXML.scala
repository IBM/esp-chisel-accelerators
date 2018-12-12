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

package esp.transforms

import esp.EspConfigAnnotation

import java.io.{File, PrintWriter}

import firrtl.{CircuitForm, CircuitState, FIRRTLException, HighForm, TargetDirAnnotation, Transform}

class EmitXML extends Transform {
  def inputForm: CircuitForm = HighForm
  def outputForm: CircuitForm = HighForm

  def execute(state: CircuitState): CircuitState = {
    lazy val targetDir: String = state.annotations.collectFirst{ case TargetDirAnnotation(d) => d }.getOrElse{
      throw new FIRRTLException("EmitXML expected to see a TargetDirAnnotation, but none found?") }
    state.annotations.collect{ case a @ EspConfigAnnotation(_, c, d) =>
      val dir = d match {
        case Left(absolute) => new File(absolute, s"${c.name}.xml")
        case Right(relative) => new File(targetDir, new File(relative, s"${c.name}.xml").toString)
      }
      val w = new PrintWriter(dir)
      w.write(a.toXML)
      w.close()
    }

    state
  }
}
