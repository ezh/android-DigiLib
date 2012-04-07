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

import android.os.Parcelable
import android.os.Parcel

case class IAmReady(val origin: Origin,
  val message: String)(implicit @transient val logger: RichLogger,
    @transient val dispatcher: Dispatcher) extends DMessage {
  if (logger != null)
    if (logger.isTraceEnabled)
      logger.infoWhere("IAmReady " + message)(3)
    else
      logger.info("IAmReady " + message)
  dispatcher.process(this)
  // parcelable interface
  def this(in: Parcel)(logger: RichLogger, dispatcher: Dispatcher) = this(origin = in.readParcelable[Origin](classOf[Origin].getClassLoader),
    message = in.readString)(logger, dispatcher)
  def writeToParcel(out: Parcel, flags: Int) {
    IAmReady.log.debug("writeToParcel IAmReady with flags " + flags)
    out.writeString(logger.getName)
    out.writeParcelable(origin, flags)
    out.writeString(message)
  }
  def describeContents() = 0
}

object IAmReady extends Logging {
  final val CREATOR: Parcelable.Creator[IAmReady] = new Parcelable.Creator[IAmReady]() {
    def createFromParcel(in: Parcel): IAmReady = try {
      log.debug("createFromParcel new IAmReady")
      val dispatcher = new Dispatcher { def process(message: DMessage) {} }
      new IAmReady(in)(null, dispatcher)
    } catch {
      case e =>
        log.error(e.getMessage, e)
        null
    }
    def newArray(size: Int): Array[IAmReady] = new Array[IAmReady](size)
  }
}