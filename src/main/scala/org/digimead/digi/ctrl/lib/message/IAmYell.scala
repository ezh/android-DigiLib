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

case class IAmYell(val origin: Origin, val message: String, val stackTrace: String, @transient val onClickCallback: Option[() => Unit],
  val ts: Long = System.currentTimeMillis)(implicit @transient val logger: RichLogger,
    @transient val dispatcher: Dispatcher) extends DMessage {
  def this(origin: Origin, message: String, t: Throwable)(implicit logger: RichLogger, dispatcher: Dispatcher) =
    this(origin, message, t.getMessage + "\n" + t.getStackTraceString, None)(logger, dispatcher)
  def this(origin: Origin, message: String, onClickCallback: Option[() => Unit])(implicit logger: RichLogger, dispatcher: Dispatcher) =
    this(origin, message, Option(new Throwable("Intospecting stack frame")).
      map(t => { t.fillInStackTrace(); t }).map(_.getStackTraceString).get, onClickCallback)(logger, dispatcher)
  def this(origin: Origin, message: String)(implicit logger: RichLogger, dispatcher: Dispatcher) =
    this(origin, message, None)(logger, dispatcher)
  if (logger != null)
    logger.error("IAmYell " + message + " ts#" + ts + "\n" + stackTrace)
  dispatcher.process(this)
  // parcelable interface
  def this(in: Parcel)(logger: RichLogger, dispatcher: Dispatcher) = this(origin = in.readParcelable[Origin](classOf[Origin].getClassLoader),
    message = in.readString, stackTrace = in.readString, onClickCallback = None, ts = in.readLong)(logger, dispatcher)
  def writeToParcel(out: Parcel, flags: Int) {
    IAmYell.log.debug("writeToParcel IAmYell with flags " + flags)
    out.writeParcelable(origin, flags)
    out.writeString(message)
    out.writeString(stackTrace)
    out.writeLong(ts)
  }
  def describeContents() = 0
}

object IAmYell extends Logging {
  final val CREATOR: Parcelable.Creator[IAmYell] = new Parcelable.Creator[IAmYell]() {
    def createFromParcel(in: Parcel): IAmYell = try {
      log.debug("createFromParcel new IAmYell")
      val dispatcher = new Dispatcher { def process(message: DMessage) {} }
      new IAmYell(in)(null, dispatcher)
    } catch {
      case e =>
        log.error(e.getMessage, e)
        null
    }
    def newArray(size: Int): Array[IAmYell] = new Array[IAmYell](size)
  }
  def apply(message: String, t: Throwable)(implicit logger: RichLogger, dispatcher: Dispatcher) =
    new IAmYell(logger, message, t)(logger, dispatcher)
  def apply(origin: Origin, message: String, t: Throwable)(implicit logger: RichLogger, dispatcher: Dispatcher) =
    new IAmYell(origin, message, t)(logger, dispatcher)
  def apply(message: String, onClickCallback: Option[() => Unit])(implicit logger: RichLogger, dispatcher: Dispatcher) =
    new IAmYell(logger, message, onClickCallback)(logger, dispatcher)
  def apply(origin: Origin, message: String, onClickCallback: Option[() => Unit])(implicit logger: RichLogger, dispatcher: Dispatcher) =
    new IAmYell(origin, message, onClickCallback)(logger, dispatcher)
  def apply(message: String)(implicit logger: RichLogger, dispatcher: Dispatcher) =
    new IAmYell(logger, message)(logger, dispatcher)
  def apply(origin: Origin, message: String)(implicit logger: RichLogger, dispatcher: Dispatcher) =
    new IAmYell(origin, message)(logger, dispatcher)
}