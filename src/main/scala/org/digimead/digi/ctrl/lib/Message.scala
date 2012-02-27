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

package org.digimead.digi.ctrl.lib

import org.digimead.digi.ctrl.lib.aop.RichLogger
import org.slf4j.Logger

sealed trait Message extends java.io.Serializable {
  val logger: Logger
  val message: String
}

object Message {
  case class IAmBusy(
    @transient val logger: Logger,
    val sender: Sender,
    val message: String) extends Message {
    logger.info("BUSY: " + message)
  }
  case class IAmReady(
    @transient val logger: Logger,
    val sender: Sender,
    val message: String) extends Message {
    logger.info("READY: " + message)
  }
  case class IAmMumble(
    @transient val logger: Logger,
    val message: String,
    @transient val onClickCallback: Option[() => Unit]) extends Message {
    logger.info(message)
  }
  case class IAmWarn(
    @transient val logger: Logger,
    val message: String,
    @transient val onClickCallback: Option[() => Unit]) extends Message {
    logger.warn(message)
  }
  case class IAmYell(
    @transient val logger: Logger,
    val message: String,
    val stackTrace: String,
    @transient val onClickCallback: Option[() => Unit]) extends Message {
    def this(logger: Logger, message: String, t: Throwable) =
      this(logger, message, t.getMessage + "\n" + t.getStackTraceString, None)
    def this(logger: Logger, message: String, onClickCallback: Option[() => Unit]) =
      this(logger, message, Option(new Throwable("Intospecting stack frame")).
        map(t => { t.fillInStackTrace(); t }).map(_.getStackTraceString).get, onClickCallback)
    def this(logger: Logger, message: String) =
      this(logger, message, None)
    logger.error(message + "\n" + stackTrace)
  }
  object IAmBusy {
    def apply(sender: Sender, message: String)(implicit logger: RichLogger) =
      new IAmBusy(logger, sender, message)
  }
  object IAmReady {
    def apply(sender: Sender, message: String)(implicit logger: RichLogger) =
      new IAmReady(logger, sender, message)
  }
  object IAmMumble {
    def apply(message: String, onClickCallback: Option[() => Unit])(implicit logger: RichLogger) =
      new IAmMumble(logger, message, onClickCallback)
    def apply(message: String)(implicit logger: RichLogger) =
      new IAmMumble(logger, message, None)
  }
  object IAmWarn {
    def apply(message: String, onClickCallback: Option[() => Unit])(implicit logger: RichLogger) =
      new IAmWarn(logger, message, onClickCallback)
    def apply(message: String)(implicit logger: RichLogger) =
      new IAmWarn(logger, message, None)
  }
  object IAmYell {
    def apply(message: String, t: Throwable)(implicit logger: RichLogger) =
      new IAmYell(logger, message, t)
    def apply(message: String, onClickCallback: Option[() => Unit])(implicit logger: RichLogger) =
      new IAmYell(logger, message, onClickCallback)
    def apply(message: String)(implicit logger: RichLogger) =
      new IAmYell(logger, message)
  }
  class Sender private (val code: Int, val name: String)
  object Sender {
    implicit def anyRefToHash(obj: AnyRef): Sender = new Sender(obj.hashCode, obj.getClass.getName())
  }
}
