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

trait Logger {
  protected var f: (Logging.Record) => Unit
  def init(context: Context) {}
  def apply(r: Logging.Record) = f(r)
  def deinit() {}
  def flush() {}
  def getF() = synchronized { f }
  def setF(_f: (Logging.Record) => Unit) = synchronized { f = _f }
}
