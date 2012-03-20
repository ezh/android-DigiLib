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

package org.digimead.digi.ctrl.lib.block

import scala.ref.WeakReference

import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.util.Android

import com.commonsware.cwac.merge.MergeAdapter

import android.app.Activity
import android.content.Context
import android.graphics.drawable.Drawable
import android.text.Html
import android.view.ContextMenu
import android.view.MenuItem
import android.view.View
import android.widget.ListView

trait Block[Item] {
  val context: Activity
  protected val items: Seq[Block.Item]
  def appendTo(adapter: MergeAdapter)
  def onListItemClick(l: ListView, v: View, item: Item)
  def onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo, item: Item) {}
  def onContextItemSelected(menuItem: MenuItem, item: Item): Boolean = false
  def reset() =
    items.foreach(_.view = new WeakReference(null))
}

object Block {
  trait Item {
    @volatile var view: WeakReference[View] = new WeakReference(null) // android built in cache may sporadically give us junk :-/
  }
  class ImageGetter(context: Context) extends Html.ImageGetter with Logging {
    def getDrawable(source: String): Drawable = {
      Android.getId(context, source, "drawable") match {
        case i if i != 0 =>
          log.debug("load drawable \"" + source + "\" with id " + i)
          context.getResources.getDrawable(i)
        case _ =>
          log.debug("drawable not found \"" + source + "\"")
          null
      }
    }
  }
}