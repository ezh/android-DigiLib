/**
 * Copyright (c) 2012 Alexey Aksenov ezh@ezh.msk.ru
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.digimead.digi.ctrl.lib.declaration

object DControlProvider {
  implicit def value2uri(v: Enumeration#Value): android.net.Uri = android.net.Uri.parse(v.toString)
  val authority = DConstant.controlPackage + ".control"
  case class Row(val session_id: Int,
    val process_id: Int,
    val component: Array[Byte],
    val executable: Array[Byte],
    val connection: Array[Byte],
    val user: Array[Byte])
  object Field extends Enumeration {
    val ID = Value("_id")
    val ProcessID = Value("process_id")
    val Component = Value("component")
    val Executable = Value("executable")
    val Connection = Value("connection")
    val User = Value("user")
  }
  object Uri extends Enumeration {
    val Session = Value("content://" + authority + "/session")
    val SessionID = Value("content://" + authority + "/session/#")
  }
}
