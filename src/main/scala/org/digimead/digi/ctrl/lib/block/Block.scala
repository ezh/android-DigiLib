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

import org.digimead.digi.ctrl.lib.AnyBase
import org.digimead.digi.ctrl.lib.androidext.XAPI
import org.digimead.digi.ctrl.lib.androidext.XResource
import org.digimead.digi.ctrl.lib.base.AppComponent
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.log.RichLogger
import org.digimead.digi.ctrl.lib.message.Dispatcher
import org.digimead.digi.ctrl.lib.message.IAmYell

import com.commonsware.cwac.merge.MergeAdapter

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.text.Html
import android.view.ContextMenu
import android.view.MenuItem
import android.view.View
import android.widget.ListView
import android.widget.Toast

trait Block[T <: Block.Item] {
  val context: Context
  def items: Seq[T]
  def appendTo(adapter: MergeAdapter)
  def onListItemClick(l: ListView, v: View, item: T)
  def onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo, item: T) {}
  def onContextItemSelected(menuItem: MenuItem, item: T): Boolean = false
  def reset() =
    items.foreach(_.view = new WeakReference(null))
}

object Block extends Logging {
  log.debug("alive")

  trait Item {
    @volatile var view: WeakReference[View] = new WeakReference(null) // android built in cache may sporadically give us junk :-/
  }
  class ImageGetter(context: Context) extends Html.ImageGetter with Logging {
    def getDrawable(source: String): Drawable = {
      XResource.getId(context, source, "drawable") match {
        case i if i != 0 =>
          log.debug("load drawable \"" + source + "\" with id " + i)
          context.getResources.getDrawable(i)
        case _ =>
          log.debug("drawable not found \"" + source + "\"")
          null
      }
    }
  }
  def copyLink(context: Context, item: Item, copyText: CharSequence)(implicit logger: RichLogger, dispatcher: Dispatcher): Boolean = {
    try {
      XAPI.clipboardManager(context).setText(copyText)
      val message = XResource.getString(context, "block_copy_link_to_clipboard").
        getOrElse("Copy link to clipboard")
      AnyBase.runOnUiThread { Toast.makeText(context, message, Toast.LENGTH_LONG).show() }
      true
    } catch {
      case e =>
        IAmYell("Unable to copy to clipboard link for " + item, e)(logger, dispatcher)
        false
    }
  }
  def sendLink(context: Context, item: Item, subjectText: CharSequence, bodyText: CharSequence)(implicit logger: RichLogger, dispatcher: Dispatcher): Boolean = {
    try {
      val intent = new Intent(Intent.ACTION_SEND)
      intent.setType("text/plain")
      intent.putExtra(Intent.EXTRA_SUBJECT, subjectText)
      intent.putExtra(Intent.EXTRA_TEXT, bodyText)
      AppComponent.Context match {
        case Some(activity) if activity.isInstanceOf[Activity] =>
          activity.startActivity(Intent.createChooser(intent, XResource.getString(activity, "share").getOrElse("share")))
        case _ => context.startActivity(Intent.createChooser(intent,
          XResource.getString(context, "share").getOrElse("share")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
      }
      true
    } catch {
      case e =>
        IAmYell("Unable 'send link' description for " + item, e)(logger, dispatcher)
        false
    }
  }
}
