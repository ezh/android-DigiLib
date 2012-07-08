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

import scala.annotation.implicitNotFound

import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.log.RichLogger
import org.digimead.digi.ctrl.lib.message.Origin.richLoggerToOrigin

import android.os.Parcelable
import android.os.Parcel

case class IAmWarn(val origin: Origin, val message: String, @transient val onClickCallback: Option[() => Unit],
  val ts: Long = System.currentTimeMillis)(implicit @transient val logger: RichLogger,
    @transient val dispatcher: Dispatcher) extends DMessage {
  if (logger != null)
    logger.warnWhere("IAmWarn " + message + " ts#" + ts, Logging.Where.ALL)
  dispatcher.process(this)
  // parcelable interface
  def this(in: Parcel)(logger: RichLogger, dispatcher: Dispatcher) = this(origin = in.readParcelable[Origin](classOf[Origin].getClassLoader),
    message = in.readString, onClickCallback = None, ts = in.readLong)(logger, dispatcher)
  def writeToParcel(out: Parcel, flags: Int) {
    if (IAmWarn.log.isTraceExtraEnabled)
      IAmWarn.log.trace("writeToParcel IAmWarn with flags " + flags)
    out.writeParcelable(origin, flags)
    out.writeString(message)
    out.writeLong(ts)
  }
  def describeContents() = 0
}

object IAmWarn extends Logging {
  final val CREATOR: Parcelable.Creator[IAmWarn] = new Parcelable.Creator[IAmWarn]() {
    def createFromParcel(in: Parcel): IAmWarn = try {
      if (log.isTraceExtraEnabled)
        log.trace("createFromParcel new IAmWarn")
      val dispatcher = new Dispatcher { def process(message: DMessage) {} }
      new IAmWarn(in)(null, dispatcher)
    } catch {
      case e =>
        log.error(e.getMessage, e)
        null
    }
    def newArray(size: Int): Array[IAmWarn] = new Array[IAmWarn](size)
  }
  def apply(message: String)(implicit logger: RichLogger, dispatcher: Dispatcher) =
    new IAmWarn(logger, message, None)(logger, dispatcher)
  def apply(origin: Origin, message: String)(implicit logger: RichLogger, dispatcher: Dispatcher) =
    new IAmWarn(origin, message, None)(logger, dispatcher)
}