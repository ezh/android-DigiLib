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

import scala.actors.Future
import scala.actors.Futures
import scala.annotation.implicitNotFound
import scala.collection.JavaConversions._
import scala.ref.WeakReference

import org.digimead.digi.ctrl.lib.AnyBase
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.base.AppComponent
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.message.Dispatcher
import org.digimead.digi.ctrl.lib.message.IAmYell
import org.digimead.digi.ctrl.lib.util.Android

import com.commonsware.cwac.merge.MergeAdapter

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.Html
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.TextView.BufferType

class LegalBlock(val context: Context,
  val lazyItems: List[Future[LegalBlock.Item]],
  _imageGetter: Html.ImageGetter = null,
  tagHandler: Html.TagHandler = null)(implicit val dispatcher: Dispatcher) extends Block[LegalBlock.Item] with Logging {
  val items = lazyItems.map(_ => LegalBlock.Item(Android.getString(context, "loading").getOrElse("loading..."))(""))
  private lazy val imageGetter = _imageGetter match {
    case null => new Block.ImageGetter(context)
    case getter => getter
  }
  private lazy val header = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater].
    inflate(Android.getId(context, "header", "layout"), null).asInstanceOf[TextView]
  private lazy val adapter = new LegalBlock.Adapter(context, android.R.layout.simple_list_item_1, items, imageGetter, tagHandler)
  private val inflater: LayoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater]
  // fill adapter with lazyItems
  Futures.future { applyFutures(lazyItems) }

  @Loggable
  def appendTo(mergeAdapter: MergeAdapter) = {
    log.debug("append " + getClass.getName + " to MergeAdapter")
    header.setText(Android.getString(context, "block_legal_title").getOrElse("legal"))
    mergeAdapter.addView(header)
    mergeAdapter.addAdapter(adapter)
  }
  @Loggable
  def onListItemClick(l: ListView, v: View, item: LegalBlock.Item) = {
    item match {
      case item: LegalBlock.Item => // show context menu
        log.debug("open context menu for item " + item)
        l.showContextMenuForChild(v)
      case item =>
        log.fatal("unsupported context menu item " + item)
    }
  }
  @Loggable
  override def onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo, item: LegalBlock.Item) {
    log.debug("create context menu for " + item)
    menu.setHeaderTitle(Android.getString(context, "block_legal_title").getOrElse("legal"))
    Android.getId(context, "ic_launcher", "drawable") match {
      case i if i != 0 =>
        menu.setHeaderIcon(i)
      case _ =>
    }
    menu.add(Menu.NONE, Android.getId(context, "block_legal_open"), 1,
      Android.getString(context, "block_legal_open").getOrElse("Open license"))
    menu.add(Menu.NONE, Android.getId(context, "block_legal_send"), 1,
      Android.getString(context, "block_legal_send").getOrElse("Send link to ..."))
  }
  @Loggable
  override def onContextItemSelected(menuItem: MenuItem, item: LegalBlock.Item): Boolean = {
    menuItem.getItemId match {
      case id if id == Android.getId(context, "block_legal_open") =>
        log.debug("open link from " + item.uri)
        try {
          val intent = new Intent(Intent.ACTION_VIEW, Uri.parse(item.uri))
          intent.addCategory(Intent.CATEGORY_BROWSABLE)
          AppComponent.Context match {
            case Some(activity) if activity.isInstanceOf[Activity] => activity.startActivity(intent)
            case _ => context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
          }
          true
        } catch {
          case e =>
            IAmYell("Unable to open license link " + item.uri, e)
            false
        }
      case id if id == Android.getId(context, "block_legal_send") =>
        log.debug("send link to " + item.uri)
        try {
          val intent = new Intent("Intent.ACTION_SEND")
          intent.setType("text/plain")
          AppComponent.Context match {
            case Some(activity) if activity.isInstanceOf[Activity] => activity.startActivity(Intent.createChooser(intent, "Send Link"))
            case _ => context.startActivity(Intent.createChooser(intent, "Send Link").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
          }
          true
        } catch {
          case e =>
            IAmYell("Unable to send license link " + item.uri, e)
            false
        }
      case id =>
        log.fatal("unknown context menu id " + id)
        false
    }
  }
  private def applyFutures(futures: List[Future[LegalBlock.Item]]) {
    val head = futures.head
    val result = Futures.awaitEither(head,
      Futures.future[Unit] { if (futures.size > 1) applyFutures(futures.tail) }) match {
        case result: LegalBlock.Item => result
        case block => head()
      }
    AnyBase.runOnUiThread {
      val index = lazyItems.indexOf(head)
      val view = items(index).view.get match {
        case Some(view) =>
          val text1 = view.findViewById(android.R.id.text1).asInstanceOf[TextView]
          text1.setText(Html.fromHtml(result.text, imageGetter, tagHandler), BufferType.SPANNABLE)
          result.view = new WeakReference(view)
        case None =>
      }
      adapter.remove(items(index))
      adapter.insert(result, index)
      adapter.notifyDataSetChanged
    }
  }
}

object LegalBlock extends Logging {
  case class Item(val text: String)(val uri: String) extends Block.Item {
    override def toString() = text
  }
  class Adapter(context: Context, textViewResourceId: Int, data: Seq[Item], imageGetter: Html.ImageGetter, tagHandler: Html.TagHandler)
    extends ArrayAdapter(context, textViewResourceId, android.R.id.text1, new java.util.ArrayList(data.toList)) {
    private val inflater: LayoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater]
    override def getView(position: Int, convertView: View, parent: ViewGroup): View = {
      val item = getItem(position)
      item.view.get match {
        case None =>
          val view = inflater.inflate(textViewResourceId, null)
          val text1 = view.findViewById(android.R.id.text1).asInstanceOf[TextView]
          text1.setTextAppearance(context, android.R.style.TextAppearance_Small)
          text1.setText(Html.fromHtml(item.text, imageGetter, tagHandler), BufferType.SPANNABLE)
          item.view = new WeakReference(view)
          view
        case Some(view) =>
          view
      }
    }
  }
}
