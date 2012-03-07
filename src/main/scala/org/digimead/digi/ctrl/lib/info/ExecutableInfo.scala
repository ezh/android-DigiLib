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

import java.util.ArrayList

import scala.collection.JavaConversions._

import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.aop.Logging
import org.digimead.digi.ctrl.lib.declaration.DState

import android.os.Parcelable
import android.os.Parcel

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
  val project: String) extends Parcelable {
  assert(id >= 0 && id <= 0xFFFF)
  assert(port == None || (port.get >= 0 && port.get <= 0xFFFF))
  assert(commandLine == None || commandLine.get.nonEmpty)
  def this(in: Parcel) = this(id = in.readInt,
    commandLine = {
      val isNone = in.readByte == 0
      if (isNone) {
        None
      } else {
        val mDataTypes = new ArrayList[String]()
        in.readStringList(mDataTypes)
        Some(mDataTypes)
      }
    },
    port = {
      val rawPort = in.readInt()
      if (rawPort == -1)
        None
      else
        Some(rawPort)
    },
    env = {
      val mDataTypes = new ArrayList[String]()
      in.readStringList(mDataTypes)
      mDataTypes
    },
    state = DState(in.readByte),
    name = in.readString,
    version = in.readString,
    description = in.readString,
    origin = in.readString,
    license = in.readString,
    project = in.readString)
  def writeToParcel(out: Parcel, flags: Int) {
    ExecutableInfo.log.debug("writeToParcel with flags " + flags)
    out.writeInt(id)
    commandLine match {
      case Some(commandLine) =>
        out.writeByte(1)
        out.writeStringArray(commandLine.toArray)
      case None =>
        out.writeByte(0)
    }
    port match {
      case Some(port) =>
        out.writeInt(port)
      case None =>
        out.writeInt(-1)
    }
    out.writeStringArray(env.toArray)
    out.writeByte(state.id.toByte)
    out.writeString(name)
    out.writeString(version)
    out.writeString(description)
    out.writeString(origin)
    out.writeString(license)
    out.writeString(project)
  }
  def describeContents() = 0
}

object ExecutableInfo extends Logging {
  override protected val log = Logging.getLogger(this)
  final val CREATOR: Parcelable.Creator[ExecutableInfo] = new Parcelable.Creator[ExecutableInfo]() {
    @Loggable
    def createFromParcel(in: Parcel): ExecutableInfo = new ExecutableInfo(in)
    def newArray(size: Int): Array[ExecutableInfo] = new Array[ExecutableInfo](size)
  }
}
