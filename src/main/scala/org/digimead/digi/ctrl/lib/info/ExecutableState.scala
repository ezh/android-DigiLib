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
import org.digimead.digi.ctrl.lib.log.Logging

import android.os.Parcelable
import android.os.Parcel
import annotation.elidable.ASSERTION

case class ExecutableState(val id: Int,
  val commandLine: Option[Seq[String]],
  val port: Option[Int],
  val env: Seq[String] = Seq(),
  val state: DState.Value) extends Parcelable {
  assert(id >= 0 && id <= 0xFFFF)
  assert(port == None || (port.get >= 0 && port.get <= 0xFFFF))
  assert(commandLine == None || commandLine.get.nonEmpty)
  def this(in: Parcel) = this(id = in.readInt,
    commandLine = {
      val dataLength = in.readInt
      if (dataLength == -1) {
        None
      } else {
        val data = new Array[String](dataLength)
        in.readStringArray(data)
        Some(data)
      }
    },
    port =
      in.readInt match {
        case -1 =>
          None
        case n =>
          Some(n)
      },
    env = {
      val dataLength = in.readInt
      val data = new Array[String](dataLength)
      in.readStringArray(data)
      data
    }, state = DState(in.readInt))
  def writeToParcel(out: Parcel, flags: Int) {
    ComponentState.log.debug("writeToParcel ExecutableState with flags " + flags)
    out.writeInt(id)
    out.writeInt(commandLine.map(_.size).getOrElse(-1))
    commandLine.foreach(cmd => out.writeStringArray(cmd.toArray))
    out.writeInt(port.getOrElse(-1))
    out.writeInt(env.size)
    out.writeStringArray(env.toArray)
    out.writeInt(state.id)
  }
  def describeContents() = 0
}

object ExecutableState extends Logging {
  override protected[lib] val log = Logging.getLogger(this)
  final val CREATOR: Parcelable.Creator[ExecutableState] = new Parcelable.Creator[ExecutableState]() {
    def createFromParcel(in: Parcel): ExecutableState = try {
      log.debug("createFromParcel new ExecutableState")
      new ExecutableState(in)
    } catch {
      case e =>
        log.error(e.getMessage, e)
        null
    }
    def newArray(size: Int): Array[ExecutableState] = new Array[ExecutableState](size)
  }
}
