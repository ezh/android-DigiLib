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

object ConsoleLogger extends Logger {
  val LINE_SEPARATOR = System.getProperty("line.separator")
  protected var f = (record: Logging.Record) => {
    System.err.println(record.toString())
    record.throwable.foreach(_.printStackTrace(System.err))
    System.err.flush()
  }
  override def flush() = System.err.flush()
}
