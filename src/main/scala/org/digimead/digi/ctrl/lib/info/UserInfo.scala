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

import org.digimead.digi.ctrl.lib.log.Logging

import android.os.Parcel
import android.os.Parcelable

case class UserInfo(name: String, password: String, home: String, enabled: Boolean) extends Parcelable {
  def this(in: Parcel) = this(name = in.readString,
    password = in.readString,
    home = in.readString,
    enabled = (in.readByte == 1))
  def writeToParcel(out: Parcel, flags: Int) {
    if (UserInfo.log.isTraceExtraEnabled)
      UserInfo.log.trace("writeToParcel UserInfo with flags " + flags)
    out.writeString(name)
    out.writeString(password)
    out.writeString(home)
    out.writeByte(if (enabled) 1 else 0)
  }
  def describeContents() = 0
}

object UserInfo extends Logging {
  override protected[lib] val log = Logging.getRichLogger(this)
  final val CREATOR: Parcelable.Creator[UserInfo] = new Parcelable.Creator[UserInfo]() {
    def createFromParcel(in: Parcel): UserInfo = try {
      if (log.isTraceExtraEnabled)
        log.trace("createFromParcel new UserInfo")
      new UserInfo(in)
    } catch {
      case e =>
        log.error(e.getMessage, e)
        null
    }
    def newArray(size: Int): Array[UserInfo] = new Array[UserInfo](size)
  }
}
