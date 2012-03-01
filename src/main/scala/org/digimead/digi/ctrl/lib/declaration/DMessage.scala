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

import scala.annotation.implicitNotFound

import org.digimead.digi.ctrl.lib.aop.RichLogger.rich2plain
import org.digimead.digi.ctrl.lib.aop.RichLogger

sealed trait DMessage extends java.io.Serializable {
  val logger: RichLogger
  val origin: DMessage.Origin
  val message: String
  val dispatcher: DMessage.Dispatcher
}

object DMessage {
  case class IAmBusy(val origin: Origin,
    val message: String)(implicit @transient val logger: RichLogger,
      @transient val dispatcher: Dispatcher) extends DMessage {
    logger.info("BUSY: " + message)
    dispatcher.process(this)
  }
  case class IAmReady(val origin: Origin,
    val message: String)(implicit @transient val logger: RichLogger,
      @transient val dispatcher: Dispatcher) extends DMessage {
    logger.info("READY: " + message)
    dispatcher.process(this)
  }
  case class IAmMumble(val origin: Origin, val message: String,
    @transient val onClickCallback: Option[() => Unit])(implicit @transient val logger: RichLogger,
      @transient val dispatcher: Dispatcher) extends DMessage {
    logger.info(message)
    dispatcher.process(this)
  }
  case class IAmWarn(val origin: Origin, val message: String,
    @transient val onClickCallback: Option[() => Unit])(implicit @transient val logger: RichLogger,
      @transient val dispatcher: Dispatcher) extends DMessage {
    logger.warn(message)
    dispatcher.process(this)
  }
  case class IAmYell(val origin: Origin, val message: String, val stackTrace: String,
    @transient val onClickCallback: Option[() => Unit])(implicit @transient val logger: RichLogger,
      @transient val dispatcher: Dispatcher) extends DMessage {
    def this(origin: Origin, message: String, t: Throwable)(implicit logger: RichLogger, dispatcher: Dispatcher) =
      this(origin, message, t.getMessage + "\n" + t.getStackTraceString, None)(logger, dispatcher)
    def this(origin: Origin, message: String, onClickCallback: Option[() => Unit])(implicit logger: RichLogger, dispatcher: Dispatcher) =
      this(origin, message, Option(new Throwable("Intospecting stack frame")).
        map(t => { t.fillInStackTrace(); t }).map(_.getStackTraceString).get, onClickCallback)(logger, dispatcher)
    def this(origin: Origin, message: String)(implicit logger: RichLogger, dispatcher: Dispatcher) =
      this(origin, message, None)(logger, dispatcher)
    logger.error(message + "\n" + stackTrace)
    dispatcher.process(this)
  }
  object IAmMumble {
    def apply(message: String)(implicit logger: RichLogger, dispatcher: Dispatcher) =
      new IAmMumble(logger, message, None)(logger, dispatcher)
    def apply(origin: Origin, message: String)(implicit logger: RichLogger, dispatcher: Dispatcher) =
      new IAmMumble(origin, message, None)(logger, dispatcher)
  }
  object IAmWarn {
    def apply(message: String)(implicit logger: RichLogger, dispatcher: Dispatcher) =
      new IAmWarn(logger, message, None)(logger, dispatcher)
    def apply(origin: Origin, message: String)(implicit logger: RichLogger, dispatcher: Dispatcher) =
      new IAmWarn(origin, message, None)(logger, dispatcher)
  }
  object IAmYell {
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
  @implicitNotFound(msg = "don't know what to do with message, please define implicit Dispatcher")
  trait Dispatcher {
    def process(message: DMessage)
  }
  case class Origin private (val code: Int, val name: String) extends java.io.Serializable
  object Origin {
    implicit def richLoggerToOrigin(logger: RichLogger): Origin = Origin(0, logger.getName())
    implicit def anyRefToOrigin(obj: AnyRef): Origin = Origin(obj.hashCode, obj.getClass.getName())
  }
}
