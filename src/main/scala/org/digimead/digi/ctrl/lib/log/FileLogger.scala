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

package org.digimead.digi.ctrl.lib.log

import android.content.Context
import java.io.PrintWriter
import java.io.BufferedWriter
import java.io.FileWriter
import java.io.File
import android.os.Environment
import org.digimead.digi.ctrl.lib.util.Common
import org.digimead.digi.ctrl.lib.base.Report
import org.digimead.digi.ctrl.lib.AnyBase

object FileLogger extends Logger with Logging {
  private[lib] var file: Option[File] = None
  private[lib] var output: Option[BufferedWriter] = None
  protected var f = (records: Seq[Logging.Record]) => synchronized {
    output.foreach {
      output =>
        output.write(records.map(r => {
          r.toString() +
            r.throwable.map(t => try {
              "\n" + t.getStackTraceString
            } catch {
              case e =>
                "stack trace \"" + t.getMessage + "\" unaviable "
            }).getOrElse("")
        }).mkString("\n"))
        output.newLine
        output.flush
    }
  }
  override def init(context: Context) = synchronized {
    try {
      val logname = Report.reportPrefix + ".log"
      deinit
      // open new
      file = AnyBase.info.get.map(info => new File(info.reportPath, logname))
      output = file.map(f => new BufferedWriter(new FileWriter(f)))
      // write header
      output.foreach(_.write(AnyBase.info.get.toString + "\n"))
      output.foreach(_.flush)
    } catch {
      case e =>
        log.error(e.getMessage, e)
    }
  }
  override def deinit() = synchronized {
    try {
      // close output if any
      output.foreach(_.close)
      output = None
      file = None
    } catch {
      case e =>
        log.error(e.getMessage, e)
    }
  }
  override def flush() = synchronized {
    try { output.foreach(_.flush) } catch { case e => log.error(e.getMessage, e) }
  }
}
