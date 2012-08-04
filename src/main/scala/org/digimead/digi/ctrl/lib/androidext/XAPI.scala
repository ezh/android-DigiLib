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

import android.content.Context
import android.view.View
import android.graphics.drawable.Drawable
import android.graphics.Point
import android.view.Display

object XAPI {
  def clipboardManager(context: Context): android.text.ClipboardManager =
    context.getSystemService(Context.CLIPBOARD_SERVICE).asInstanceOf[android.text.ClipboardManager]
  def setViewBackground(view: View, background: Drawable) {
    try {
      val setBackground = classOf[View].getMethod("setBackground", Array[Class[_]](classOf[Drawable]): _*)
      setBackground.invoke(view, background)
    } catch {
      case e: NoSuchMethodException =>
        // prevent deprecated warning
        val setBackgroundDrawableMethod = classOf[View].getMethod("setBackgroundDrawable", Array[Class[_]](classOf[Drawable]): _*)
        setBackgroundDrawableMethod.invoke(view, background)
    }
  }
  def getDisplaySize(display: Display): Point = {
    val size = new Point
    try {
      val getSizeMethod = classOf[Display].getMethod("getSize", Array[Class[_]](classOf[Point]): _*)
      getSizeMethod.invoke(display, size)
    } catch {
      case e: NoSuchMethodException =>
        val getWidthMethod = classOf[Display].getMethod("getWidth")
        val getHeightMethod = classOf[Display].getMethod("getHeight")
        size.x = getWidthMethod.invoke(display).asInstanceOf[Int]
        size.y = getHeightMethod.invoke(display).asInstanceOf[Int]
    }
    size
  }
}
