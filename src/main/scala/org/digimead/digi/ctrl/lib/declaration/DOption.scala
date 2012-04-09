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

import org.digimead.digi.ctrl.lib.util.Android

import android.content.Context

object DOption extends Enumeration {
  val CachePeriod: OptVal = Value("cache_period")
  val CacheFolder: OptVal = Value("cache_dir")
  val CacheClass: OptVal = Value("cache_class")
  val ConfirmConn: OptVal = Value("confirm_connection")
  val WriteConnLog: OptVal = Value("write_connection_log")
  val AsRoot: OptVal = Value("as_root")
  val OnBoot: OptVal = Value("on_boot")
  val Port: OptVal = Value("port", classOf[Int])
  class OptVal(val r: String, val kind: Class[_], _name: String, _description: String) extends Val(nextId, r) {
    def name(context: Context) = Android.getString(context, _name).getOrElse(_name)
    def description(context: Context) = Android.getString(context, _description).getOrElse(_description)
  }
  object OptVal {
    implicit def value2string_id(v: OptVal): String = v.r
  }
  protected final def Value(id: String, kind: Class[_] = classOf[Boolean], _name: String = null, _description: String = null): OptVal = {
    val name = if (_name != null) _name else "option_" + id + "_name"
    val description = if (_description != null) _description else "option_" + id + "_description"
    new OptVal(id, kind, name, description)
  }
}
