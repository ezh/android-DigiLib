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

import scala.annotation.implicitNotFound
import scala.ref.WeakReference

import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.message.Dispatcher
import org.digimead.digi.ctrl.lib.message.IAmYell
import org.digimead.digi.ctrl.lib.util.Android

import com.commonsware.cwac.merge.MergeAdapter

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
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView

// TODO ui translation help: LANG as link
// TODO documentation translation help: LANG as link
// TODO web page/description translation help: LANG as link
class CommunityBlock(val context: Context, val xdaUri: Option[Uri],
  val wikiUri: Option[Uri])(implicit val dispatcher: Dispatcher) extends Block[CommunityBlock.Item] with Logging {
  val itemXDA = CommunityBlock.Item(Android.getString(context, "block_community_xda_title").getOrElse("XDA developers community"),
    Android.getString(context, "block_community_xda_description").getOrElse("XDA forum thread"), "ic_block_community_xda_logo")
  val itemWiki = CommunityBlock.Item(Android.getString(context, "block_community_wiki_title").getOrElse("wiki"),
    Android.getString(context, "block_community_wiki_description").getOrElse("collaborate on a documentation"), "ic_block_community_wiki")
  val itemTranslate = CommunityBlock.Item(Android.getString(context, "block_community_translate_title").getOrElse("translate"),
    Android.getString(context, "block_community_translate_description").getOrElse("add new or improve translation"), "ic_block_community_translate")
  val items = Seq() ++ (if (xdaUri != None) Seq(itemXDA) else Seq()) ++
    (if (wikiUri != None) Seq(itemWiki) else Seq())
  private lazy val header = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater].
    inflate(Android.getId(context, "header", "layout"), null).asInstanceOf[TextView]
  private lazy val adapter = new CommunityBlock.Adapter(context, Android.getId(context, "block_list_item", "layout"), items)
  @Loggable
  def appendTo(mergeAdapter: MergeAdapter) = {
    log.debug("append " + getClass.getName + " to MergeAdapter")
    header.setText(Html.fromHtml(Android.getString(context, "block_community_title").getOrElse("community")))
    mergeAdapter.addView(header)
    mergeAdapter.addAdapter(adapter)
  }
  @Loggable
  def onListItemClick(l: ListView, v: View, item: CommunityBlock.Item) = {
    item match {
      case this.itemXDA => // jump to XDA
        log.debug("open XDA page at " + xdaUri.get)
        try {
          val intent = new Intent(Intent.ACTION_VIEW, xdaUri.get)
          intent.addCategory(Intent.CATEGORY_BROWSABLE)
          context.startActivity(intent)
        } catch {
          case e =>
            IAmYell("Unable to open XDA thread page: " + xdaUri.get, e)
        }
      case this.itemWiki => // jump to project
        log.debug("open wiki page at " + wikiUri.get)
        try {
          val intent = new Intent(Intent.ACTION_VIEW, wikiUri.get)
          intent.addCategory(Intent.CATEGORY_BROWSABLE)
          context.startActivity(intent)
        } catch {
          case e =>
            IAmYell("Unable to open wiki page: " + wikiUri.get, e)
        }
    }
  }
  @Loggable
  override def onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo, item: CommunityBlock.Item) {
    log.debug("create context menu for " + item.name)
    menu.setHeaderTitle(item.name)
    if (item.icon.nonEmpty)
      Android.getId(context, item.icon, "drawable") match {
        case i if i != 0 =>
          menu.setHeaderIcon(i)
        case _ =>
      }
    item match {
      case this.itemXDA =>
        menu.add(Menu.NONE, Android.getId(context, "block_link_copy"), 1,
          Android.getString(context, "block_link_copy").getOrElse("Copy link"))
        menu.add(Menu.NONE, Android.getId(context, "block_link_send"), 2,
          Android.getString(context, "block_link_send").getOrElse("Send link to ..."))
      case this.itemWiki =>
        menu.add(Menu.NONE, Android.getId(context, "block_link_copy"), 1,
          Android.getString(context, "block_link_copy").getOrElse("Copy link"))
        menu.add(Menu.NONE, Android.getId(context, "block_link_send"), 2,
          Android.getString(context, "block_link_send").getOrElse("Send link to ..."))
      case item =>
        log.fatal("unsupported context menu item " + item)
    }
  }
  @Loggable
  override def onContextItemSelected(menuItem: MenuItem, item: CommunityBlock.Item): Boolean = {
    item match {
      case this.itemXDA =>
        menuItem.getItemId match {
          case id if id == Android.getId(context, "block_link_copy") =>
            Block.copyLink(context, item, xdaUri.get.toString)
          case id if id == Android.getId(context, "block_link_send") =>
            Block.sendLink(context, item, item.name, xdaUri.get.toString)
          case message =>
            log.fatal("skip unknown message " + message)
            false
        }
      case this.itemWiki =>
        menuItem.getItemId match {
          case id if id == Android.getId(context, "block_link_copy") =>
            Block.copyLink(context, item, wikiUri.get.toString)
          case id if id == Android.getId(context, "block_link_send") =>
            Block.sendLink(context, item, item.name, wikiUri.get.toString)
          case message =>
            log.fatal("skip unknown message " + message)
            false
        }
      case item =>
        log.fatal("unsupported context menu item " + item)
        false
    }
  }
}

object CommunityBlock {
  private val name = "name"
  private val description = "description"
  case class Item(name: String, description: String, icon: String = "") extends Block.Item
  class Adapter(context: Context, textViewResourceId: Int, data: Seq[Item])
    extends ArrayAdapter(context, textViewResourceId, android.R.id.text1, data.toArray) {
    private var inflater: LayoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater]
    override def getView(position: Int, convertView: View, parent: ViewGroup): View = {
      val item = data(position)
      item.view.get match {
        case None =>
          val view = inflater.inflate(textViewResourceId, null)
          val text1 = view.findViewById(android.R.id.text1).asInstanceOf[TextView]
          val text2 = view.findViewById(android.R.id.text2).asInstanceOf[TextView]
          val icon = view.findViewById(android.R.id.icon1).asInstanceOf[ImageView]
          text2.setVisibility(View.VISIBLE)
          text1.setText(Html.fromHtml(item.name))
          text2.setText(Html.fromHtml(item.description))
          if (item.icon.nonEmpty)
            Android.getId(context, item.icon, "drawable") match {
              case i if i != 0 =>
                icon.setVisibility(View.VISIBLE)
                icon.setImageDrawable(context.getResources.getDrawable(i))
              case _ =>
            }
          item.view = new WeakReference(view)
          view
        case Some(view) =>
          view
      }
    }
  }
}
