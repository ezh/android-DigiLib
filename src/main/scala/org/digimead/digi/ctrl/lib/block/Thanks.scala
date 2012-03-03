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

import org.digimead.digi.ctrl.lib.aop.RichLogger.rich2plain
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.aop.Logging
import org.digimead.digi.ctrl.lib.util.Android

import com.commonsware.cwac.merge.MergeAdapter

import android.app.Activity
import android.text.Html
import android.text.Spanned
import android.view.View
import android.widget.ListView
import android.widget.SimpleAdapter
import android.widget.TextView

class Thanks(val context: Activity) extends Block[Thanks.Item] with Logging {
  private val items = Seq(
    Thanks.Item("Someone", "For everything"))
  private lazy val header = context.getLayoutInflater.inflate(Android.getId(context, "header", "layout"), null).asInstanceOf[TextView]
  private lazy val adapter = new SimpleAdapter(context, Thanks.getListValues(items), android.R.layout.simple_list_item_2,
    Array(Thanks.name, Thanks.description), Array(android.R.id.text1, android.R.id.text2))
  @Loggable
  def appendTo(mergeAdapter: MergeAdapter) = {
    log.debug("append " + getClass.getName + " to MergeAdapter")
    header.setText(Html.fromHtml(Android.getString(context, "block_thanks_title").getOrElse("thanks")))
    mergeAdapter.addView(header)
    mergeAdapter.addAdapter(adapter)
  }
  @Loggable
  def onListItemClick(l: ListView, v: View, item: Thanks.Item) = {
  }
}

object Thanks {
  private val name = "name"
  private val description = "description"
  case class Item(name: String, description: String) extends java.util.HashMap[String, Spanned] {
    put(Thanks.name, Html.fromHtml(name))
    put(Thanks.description, Html.fromHtml(description))
  }
  def getListValues(menu: Seq[Item]): java.util.List[java.util.HashMap[String, Spanned]] = {
    val values = new ArrayList[java.util.HashMap[String, Spanned]]()
    menu.foreach(values.add)
    values
  }
}