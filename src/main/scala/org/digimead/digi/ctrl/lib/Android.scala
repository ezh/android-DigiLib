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

import android.content.Context

object Android {
  def getString(context: Context, name: String): Option[String] =
    getId(context, name, "string") match {
      case 0 =>
        None
      case id =>
        Option(context.getString(id))
    }
  def getString(context: Context, name: String, formatArgs: AnyRef*): Option[String] =
    getId(context, name, "string") match {
      case 0 =>
        None
      case id =>
        Option(context.getString(id, formatArgs: _*))
    }
  def getCapitalized(context: Context, name: String) =
    getString(context, name).map(s => if (s.length > 1)
      s(0).toUpper + s.substring(1)
    else
      s.toUpperCase)
  def getCapitalized(context: Context, name: String, formatArgs: AnyRef*) =
    getString(context, name, formatArgs: _*).map(s => if (s.length > 1)
      s(0).toUpper + s.substring(1)
    else
      s.toUpperCase)
  def getId(context: Context, name: String, scope: String = "id") =
    context.getResources().getIdentifier(name, scope, context.getPackageName())
}
