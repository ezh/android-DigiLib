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

import org.digimead.digi.ctrl.lib.log.Logging

import android.os.Parcelable
import android.os.Parcel

case class DConnection(
  val connectionID: Int,
  val processID: Int,
  val key: Int) extends Parcelable {
  var FD, localIP, localPort, remoteIP, remotePort, PID, UID, GID = -1
  var timestamp: Long = -1
  var cmd: String = ""
  def this(in: Parcel) = this(connectionID = in.readInt,
    processID = in.readInt,
    key = in.readInt)
  def writeToParcel(out: Parcel, flags: Int) {
    DConnection.log.debug("writeToParcel DConnection with flags " + flags)
    out.writeInt(connectionID)
    out.writeInt(processID)
    out.writeInt(key)
    out.writeInt(FD)
    out.writeInt(localIP)
    out.writeInt(remoteIP)
    out.writeInt(remotePort)
    out.writeInt(PID)
    out.writeInt(UID)
    out.writeInt(GID)
    out.writeLong(timestamp)
    out.writeString(cmd)
  }
  def describeContents() = 0
}

object DConnection extends Logging {
  override protected[lib] val log = Logging.getLogger(this)
  final val CREATOR: Parcelable.Creator[DConnection] = new Parcelable.Creator[DConnection]() {
    def createFromParcel(in: Parcel): DConnection = try {
      log.debug("createFromParcel new DConnection")
      val obj = new DConnection(in)
      obj.FD = in.readInt()
      obj.localIP = in.readInt()
      obj.remoteIP = in.readInt()
      obj.remotePort = in.readInt()
      obj.PID = in.readInt()
      obj.UID = in.readInt()
      obj.GID = in.readInt()
      obj.timestamp = in.readLong()
      obj.cmd = in.readString()
      obj
    } catch {
      case e =>
        log.error(e.getMessage, e)
        null
    }
    def newArray(size: Int): Array[DConnection] = new Array[DConnection](size)
  }
}
