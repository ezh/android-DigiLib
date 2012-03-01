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

package org.digimead.digi.ctrl.lib.info

import scala.annotation.elidable

import org.digimead.digi.ctrl.lib.declaration.DState

import annotation.elidable.ASSERTION

case class ExecutableInfo(val id: Int,
  val commandLine: Option[Seq[String]],
  val port: Option[Int],
  val env: Seq[String],
  val state: DState.Value,
  val name: String,
  val version: String,
  val description: String,
  val origin: String,
  val license: String,
  val project: String) extends java.io.Serializable {
  assert(id >= 0 && id <= 0xFFFF)
  assert(port == None || (port.get >= 0 && port.get <= 0xFFFF))
  assert(commandLine == None || commandLine.get.nonEmpty)
}