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

package org.digimead.digi.ctrl.lib.androidext

import java.io.IOException

import scala.ref.WeakReference

import org.digimead.digi.ctrl.lib.log.Logging
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

import android.content.Context
import android.util.AttributeSet
import android.util.Xml

object XResource extends Logging {
  def getString(context: WeakReference[Context], name: String): Option[String] =
    context.get.flatMap(ctx => getString(ctx, name))
  def getString(context: Context, name: String): Option[String] =
    getId(context, name, "string") match {
      case 0 =>
        None
      case id =>
        Option(context.getString(id))
    }
  def getString(context: WeakReference[Context], name: String, formatArgs: AnyRef*): Option[String] =
    context.get.flatMap(ctx => getString(ctx, name, formatArgs: _*))
  def getString(context: Context, name: String, formatArgs: AnyRef*): Option[String] =
    getId(context, name, "string") match {
      case 0 =>
        None
      case id =>
        Option(context.getString(id, formatArgs: _*))
    }
  def getCapitalized(context: WeakReference[Context], name: String): Option[String] =
    context.get.flatMap(ctx => getCapitalized(ctx, name))
  def getCapitalized(context: Context, name: String) =
    getString(context, name).map(s => if (s.length > 1)
      s(0).toUpper + s.substring(1)
    else
      s.toUpperCase)
  def getCapitalized(context: WeakReference[Context], name: String, formatArgs: AnyRef*): Option[String] =
    context.get.flatMap(ctx => getCapitalized(ctx, name, formatArgs: _*))
  def getCapitalized(context: Context, name: String, formatArgs: AnyRef*) =
    getString(context, name, formatArgs: _*).map(s => if (s.length > 1)
      s(0).toUpper + s.substring(1)
    else
      s.toUpperCase)
  def getId(context: WeakReference[Context], name: String): Int =
    getId(context, name, "id")
  def getId(context: WeakReference[Context], name: String, scope: String): Int =
    context.get.map(ctx => getId(ctx, name, scope)).getOrElse(0)
  def getId(context: Context, name: String): Int =
    getId(context, name, "id")
  def getId(context: Context, name: String, scope: String): Int =
    context.getResources().getIdentifier(name, scope, context.getPackageName())
  def getAttributeSet(context: Context, layout: Int, filter: XmlPullParser => Boolean = null): Option[AttributeSet] = {
    val parser = context.getResources.getLayout(layout)
    var state = 0
    do {
      try {
        state = parser.next()
      } catch {
        case e: XmlPullParserException =>
          log.error(e.getMessage)
        case e: IOException =>
          log.error(e.getMessage)
      }
      if (state == XmlPullParser.START_TAG) {
        if (filter == null)
          return Some(Xml.asAttributeSet(parser))
        else if (filter(parser))
          return Some(Xml.asAttributeSet(parser))
      }
    } while (state != XmlPullParser.END_DOCUMENT)
    None
  }
}
