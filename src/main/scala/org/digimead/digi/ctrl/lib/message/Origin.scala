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

package org.digimead.digi.ctrl.lib.message

import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.log.RichLogger

import android.os.Parcelable
import android.os.Parcel

case class Origin private (val code: Int, val name: String) extends Parcelable {
  def this(in: Parcel) =
    this(code = in.readInt, name = in.readString)
  def writeToParcel(out: Parcel, flags: Int) {
    Origin.log.debug("writeToParcel Origin with flags " + flags)
    out.writeInt(code)
    out.writeString(name)
  }
  def describeContents() = 0
}
object Origin extends Logging {
  implicit def richLoggerToOrigin(logger: RichLogger): Origin = Origin(0, logger.getName())
  implicit def anyRefToOrigin(obj: AnyRef): Origin = Origin(obj.hashCode, obj.getClass.getName())
  final val CREATOR: Parcelable.Creator[Origin] = new Parcelable.Creator[Origin]() {
    def createFromParcel(in: Parcel): Origin = try {
      log.debug("createFromParcel new Origin")
      new Origin(in)
    } catch {
      case e =>
        log.error(e.getMessage, e)
        null
    }
    def newArray(size: Int): Array[Origin] = new Array[Origin](size)
  }
}