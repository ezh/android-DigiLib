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

import org.digimead.digi.ctrl.lib.aop.RichLogger.rich2plain
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.aop.Logging
import org.digimead.digi.ctrl.lib.declaration.DMessage.Dispatcher
import org.digimead.digi.ctrl.lib.declaration.DMessage
import org.digimead.digi.ctrl.lib.util.Android

import com.commonsware.cwac.merge.MergeAdapter

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.Html
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView

class Community(val context: Activity,
  val wikiUri: Uri)(implicit @transient val dispatcher: Dispatcher) extends Block[Community.Item] with Logging {
  val itemWiki = Community.Item(Android.getString(context, "block_community_wiki_title").getOrElse("wiki"),
    Android.getString(context, "block_community_wiki_description").getOrElse("collaborate on a documentation"), "ic_block_community_wiki")
  val itemTranslate = Community.Item(Android.getString(context, "block_community_translate_title").getOrElse("translate"),
    Android.getString(context, "block_community_translate_description").getOrElse("add new or improve translation"), "ic_block_community_translate")
  private val items = Seq(itemWiki)
  private lazy val header = context.getLayoutInflater.inflate(Android.getId(context, "header", "layout"), null).asInstanceOf[TextView]
  private lazy val adapter = new Community.Adapter(context, items)
  @Loggable
  def appendTo(mergeAdapter: MergeAdapter) = {
    log.debug("append " + getClass.getName + " to MergeAdapter")
    header.setText(Html.fromHtml(Android.getString(context, "block_community_title").getOrElse("community")))
    mergeAdapter.addView(header)
    mergeAdapter.addAdapter(adapter)
  }
  @Loggable
  def onListItemClick(l: ListView, v: View, item: Community.Item) = {
    item match {
      case this.itemWiki => // jump to project
        log.debug("open wiki page at " + wikiUri)
        try {
          val intent = new Intent(Intent.ACTION_VIEW, wikiUri)
          intent.addCategory(Intent.CATEGORY_BROWSABLE)
          context.startActivity(intent)
        } catch {
          case e =>
            DMessage.IAmYell("Unable to open wiki page: " + wikiUri, e)
        }
    }
  }
}

object Community {
  private val name = "name"
  private val description = "description"
  case class Item(name: String, description: String, icon: String = "")
  class Adapter(context: Context, data: Seq[Item])
    extends ArrayAdapter(context, Android.getId(context, "advanced_list_item", "layout"), android.R.id.text1, data.toArray) {
    override def getView(position: Int, convertView: View, parent: ViewGroup): View = {
      convertView match {
        case null =>
          val view = super.getView(position, convertView, parent)
          val item = data(position)
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
          view
        case view: View =>
          view
      }
    }
  }
}
