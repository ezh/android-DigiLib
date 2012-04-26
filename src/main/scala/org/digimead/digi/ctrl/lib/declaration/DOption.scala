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
import scala.runtime.Boxed

object DOption extends Enumeration {
  val CachePeriod: OptVal = Value("cache_period", classOf[Int], (1000 * 60 * 10): java.lang.Integer) // 10 minutes
  val CacheFolder: OptVal = Value("cache_dir", classOf[String], ".")
  val CacheClass: OptVal = Value("cache_class", classOf[String], DConstant.prefix + "lib.base.AppCache")
  val ConfirmConn: OptVal = Value("confirm_connection", classOf[Boolean], true: java.lang.Boolean)
  val WriteConnLog: OptVal = Value("write_connection_log", classOf[Boolean], false: java.lang.Boolean)
  val ACLConnection: OptVal = Value("acl_connection_allow", classOf[Boolean], true: java.lang.Boolean)
  val AsRoot: OptVal = Value("as_root", classOf[Boolean], false: java.lang.Boolean)
  val OnBoot: OptVal = Value("on_boot", classOf[Boolean], false: java.lang.Boolean)
  val Port: OptVal = Value("port", classOf[Int], 2222: java.lang.Integer)
  class OptVal(val r: String, val kind: Class[_], val default: AnyRef, _name: String, _description: String) extends Val(nextId, r) {
    def name(context: Context) = Android.getString(context, _name).getOrElse(_name)
    def description(context: Context) = Android.getString(context, _description).getOrElse(_description)
  }
  object OptVal {
    implicit def value2string_id(v: OptVal): String = v.r
  }
  protected final def Value(id: String, kind: Class[_], default: AnyRef, _name: String = null, _description: String = null): OptVal = {
    val name = if (_name != null) _name else "option_" + id + "_name"
    val description = if (_description != null) _description else "option_" + id + "_description"
    new OptVal(id, kind, default, name, description)
  }
}
