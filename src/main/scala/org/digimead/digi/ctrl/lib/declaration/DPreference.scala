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

object DPreference {
  val Main = getClass.getPackage.getName + "@main" // shared preferences name
  val FilterInterface = getClass.getPackage.getName + "@filter_interface" // shared preferences name
  val FilterConnection = getClass.getPackage.getName + "@filter_connection" // shared preferences name
  val Log = getClass.getPackage.getName + "@log" // shared preferences name
}
