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
  // TODO rewrite with nameMap = LongMap(id) -> names and descriptionMap SoftReference
  val CachePeriod = Value("cache_period", "cache_period", "cache_period")
  val CacheFolder = Value("cache_dir", "cache_dir", "cache_dir")
  val CacheClass = Value("cache_class", "cache_class", "cache_class")
  val CommConfirmation = Value("comm_confirmation", "comm_confirmation_name", "comm_confirmation_description")
  val CommWriteLog = Value("comm_writelog", "comm_writelog_name", "comm_writelog_description")
  val AsRoot = Value("asroot", "service_asroot_name", "service_asroot_description")
  val Running = Value("running", "service_running_name", "service_running_description")
  val OnBoot = Value("onboot", "service_onboot_name", "service_onboot_description")
  class OptVal(val res: String, val name: String, val description: String) extends Val(nextId, name) {
    def name(context: Context) = Android.getString(context, res)
    def description(context: Context) = Android.getString(context, res)
  }
  protected final def Value(id: String, name: String, description: String): OptVal =
    new OptVal(id, name, description)
}