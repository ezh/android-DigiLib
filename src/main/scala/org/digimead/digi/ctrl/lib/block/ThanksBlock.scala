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

import java.util.ArrayList

import org.digimead.digi.ctrl.lib.androidext.XResource
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.log.Logging

import com.commonsware.cwac.merge.MergeAdapter

import android.content.Context
import android.text.Html
import android.text.Spanned
import android.view.LayoutInflater
import android.view.View
import android.widget.ListView
import android.widget.SimpleAdapter
import android.widget.TextView

// TODO check apk key (debug/release): ask + market link or respect
// TODO list of contributor
class ThanksBlock(val context: Context) extends Block[ThanksBlock.Item] with Logging {
  val items = Seq(
    ThanksBlock.Item("Someone", "For everything"))
  private lazy val header = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater].
    inflate(XResource.getId(context, "header", "layout"), null).asInstanceOf[TextView]
  private lazy val adapter = new SimpleAdapter(context, ThanksBlock.getListValues(items), android.R.layout.simple_list_item_2,
    Array(ThanksBlock.name, ThanksBlock.description), Array(android.R.id.text1, android.R.id.text2))
  @Loggable
  def appendTo(mergeAdapter: MergeAdapter) = {
    log.debug("append " + getClass.getName + " to MergeAdapter")
    header.setText(Html.fromHtml(XResource.getString(context, "block_thanks_title").getOrElse("thanks")))
    mergeAdapter.addView(header)
    mergeAdapter.addAdapter(adapter)
  }
  @Loggable
  def onListItemClick(l: ListView, v: View, item: ThanksBlock.Item) = {
  }
}

object ThanksBlock {
  private val name = "name"
  private val description = "description"
  case class Item(name: String, description: String) extends java.util.HashMap[String, Spanned] with Block.Item {
    put(ThanksBlock.name, Html.fromHtml(name))
    put(ThanksBlock.description, Html.fromHtml(description))
  }
  def getListValues(menu: Seq[Item]): java.util.List[java.util.HashMap[String, Spanned]] = {
    val values = new ArrayList[java.util.HashMap[String, Spanned]]()
    menu.foreach(values.add)
    values
  }
}
