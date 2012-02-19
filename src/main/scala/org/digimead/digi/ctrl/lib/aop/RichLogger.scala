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

package org.digimead.digi.ctrl.lib.aop

import org.slf4j.Logger

class RichLogger(val logger: Logger) {
  // fast look while development, highlight it in your IDE
  def _g_a_s_e_(msg: String) {
    fatal("GASE: " + msg)
  }
  // error with stack trace
  def fatal(msg: String) {
    val t = new Throwable("Intospecting stack frame")
    t.fillInStackTrace()
    logger.error(msg + "\n" + t.getStackTraceString)    
  }
}

object RichLogger {
  implicit def rich2plain(rich: RichLogger): Logger = rich.logger
}