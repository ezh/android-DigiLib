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

package org.digimead.digi.ctrl.lib.util

import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.Thread.UncaughtExceptionHandler

import org.digimead.digi.ctrl.lib.aop.Logging
import org.digimead.digi.ctrl.lib.base.AppActivity
import org.digimead.digi.ctrl.lib.declaration.DIntent
import org.digimead.digi.ctrl.lib.AnyBase

import android.content.Context
import android.content.Intent

class ExceptionHandler extends Logging {
  def register(context: Context) {
    // don't register again if already registered
    val currentHandler = Thread.getDefaultUncaughtExceptionHandler()
    if (currentHandler.isInstanceOf[ExceptionHandler.Default])
      return
    log.debug("registering default exceptions handler")
    if (currentHandler != null)
      log.debug("current handler class=" + currentHandler.getClass.getName())
    // register default exceptions handler
    Thread.setDefaultUncaughtExceptionHandler(new ExceptionHandler.Default(currentHandler))
  }
}

object ExceptionHandler extends Logging {
  class Default(val defaultExceptionHandler: UncaughtExceptionHandler) extends UncaughtExceptionHandler with Logging {
    // Default exception handler
    def uncaughtException(t: Thread, e: Throwable) = AnyBase.info.get.foreach {
      info =>
        // Here you should have a more robust, permanent record of problems
        val reportName = "stacktrace" + Logging.Report.reportSuffix
        val result = new StringWriter()
        val printWriter = new PrintWriter(result)
        e.printStackTrace(printWriter)
        try {
          val file = new File(info.reportPath, reportName)
          log.debug("Writing unhandled exception to: " + file)
          // Write the stacktrace to disk
          val bos = new BufferedWriter(new FileWriter(file))
          bos.write(AnyBase.info.get.get.toString)
          bos.write(result.toString())
          bos.flush()
          // Close up everything
          bos.close()
          AppActivity.Context.foreach(_.sendBroadcast(new Intent(DIntent.Error))) // try to notify user, if it is possible
        } catch {
          // Nothing much we can do about this - the game is over
          case e =>
            log.error(e.getMessage, e)
        }
        //call original handler
        defaultExceptionHandler.uncaughtException(t, e)
    }
  }
}
