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

import java.util.concurrent.atomic.AtomicInteger

import scala.ref.WeakReference

import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.declaration.DMessage.Dispatcher
import org.digimead.digi.ctrl.lib.declaration.DMessage
import org.digimead.digi.ctrl.lib.util.Android

import com.commonsware.cwac.merge.MergeAdapter

import android.app.Activity
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

class SupportBlock(val context: Activity,
  val projectUri: Uri,
  val issuesUri: Uri,
  val emailTo: String,
  val emailSubject: String,
  val voicePhone: String,
  val skypeUser: String)(implicit @transient val dispatcher: Dispatcher) extends Block[SupportBlock.Item] with Logging {
  val itemProject = SupportBlock.Item(Android.getString(context, "block_support_project_title").getOrElse("project %s").format(Android.getString(context, "app_name").get),
    Android.getString(context, "block_support_project_description").getOrElse("open %s project web site").format(Android.getString(context, "app_name").get), "ic_block_support_project")
  val itemIssues = SupportBlock.Item(Android.getString(context, "block_support_issues_title").getOrElse("view or submit an issue"),
    Android.getString(context, "block_support_issues_description").getOrElse("bug reports, feature requests and enhancements"), "ic_block_support_issues")
  val itemEmail = SupportBlock.Item(Android.getString(context, "block_email_title").getOrElse("send message"),
    Android.getString(context, "block_support_email_description").getOrElse("email us directly"), "ic_block_support_message")
  val itemChat = SupportBlock.Item(Android.getString(context, "block_chat_title").getOrElse("live chat"),
    Android.getString(context, "block_support_chat_description").getOrElse("let's talk via Skype, VoIP, ..."), "ic_block_support_chat")
  val items = Seq(itemProject, itemIssues, itemEmail, itemChat)
  private lazy val header = context.getLayoutInflater.inflate(Android.getId(context, "header", "layout"), null).asInstanceOf[TextView]
  private lazy val adapter = new SupportBlock.Adapter(context, Android.getId(context, "block_list_item", "layout"), items)
  @Loggable
  def appendTo(mergeAdapter: MergeAdapter) = {
    log.debug("append " + getClass.getName + " to MergeAdapter")
    header.setText(Html.fromHtml(Android.getString(context, "block_support_title").getOrElse("support")))
    mergeAdapter.addView(header)
    mergeAdapter.addAdapter(adapter)
  }
  @Loggable
  def onListItemClick(l: ListView, v: View, item: SupportBlock.Item) = {
    item match {
      case this.itemProject => // jump to project
        log.debug("open project web site at " + projectUri)
        try {
          val intent = new Intent(Intent.ACTION_VIEW, projectUri)
          intent.addCategory(Intent.CATEGORY_BROWSABLE)
          context.startActivity(intent)
        } catch {
          case e =>
            DMessage.IAmYell("Unable to open project link: " + projectUri, e)
        }
      case this.itemIssues => // jump to issues
        log.debug("open issues web page at " + projectUri)
        try {
          val intent = new Intent(Intent.ACTION_VIEW, issuesUri)
          intent.addCategory(Intent.CATEGORY_BROWSABLE)
          context.startActivity(intent)
        } catch {
          case e =>
            DMessage.IAmYell("Unable to open project link: " + issuesUri, e)
        }
      case this.itemEmail => // create email
        // TODO simple email vs complex with log
        log.debug("send email to " + emailTo)
        try {
          val intent = new Intent(Intent.ACTION_SEND)
          intent.putExtra(Intent.EXTRA_EMAIL, Array[String](emailTo, ""))
          intent.putExtra(Intent.EXTRA_SUBJECT, emailSubject)
          intent.putExtra(android.content.Intent.EXTRA_TEXT, "")
          intent.setType("text/plain");
          context.startActivity(Intent.createChooser(intent, Android.getString(context, "share").getOrElse("share")))
        } catch {
          case e =>
            DMessage.IAmYell("Unable 'send to' email: " + emailTo + " / " + emailSubject, e)
        }
      case this.itemChat => // show context menu with call/skype
        log.debug("open context menu for voice call")
        l.showContextMenuForChild(v)
      case item =>
        log.fatal("unsupported context menu item " + item)
    }
  }
  @Loggable
  override def onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo, item: SupportBlock.Item) {
    log.debug("create context menu for " + item.name)
    menu.setHeaderTitle(Android.getString(context, "context_menu").getOrElse("Context Menu"))
    //inner.icon(this).map(menu.setHeaderIcon(_))
    menu.add(Menu.NONE, Android.getId(context, "block_support_voice_call"), 1,
      Android.getString(context, "block_support_voice_call").getOrElse("Voice call"))
    menu.add(Menu.NONE, Android.getId(context, "block_support_skype_call"), 1,
      Android.getString(context, "block_support_skype_call").getOrElse("Skype call"))
  }
  @Loggable
  override def onContextItemSelected(menuItem: MenuItem, item: SupportBlock.Item): Boolean = {
    menuItem.getItemId match {
      case id if id == Android.getId(context, "block_support_voice_call") =>
        log.debug("start voice call to " + voicePhone)
        try {
          val intent = new Intent(Intent.ACTION_CALL)
          intent.setData(Uri.parse("tel:" + voicePhone))
          context.startActivity(intent)
          true
        } catch {
          case e =>
            DMessage.IAmYell("Unable start voice call to " + voicePhone, e)
            false
        }
      case id if id == Android.getId(context, "block_support_skype_call") =>
        log.debug("start skype call to " + skypeUser)
        try {
          val intent = new Intent("android.intent.action.VIEW")
          intent.setData(Uri.parse("skype:" + skypeUser))
          context.startActivity(intent)
          true
        } catch {
          case e =>
            DMessage.IAmYell("Unable start skype call to " + skypeUser, e)
            false
        }
      case id =>
        log.fatal("unknown context menu id " + id)
        false
    }
  }
}

object SupportBlock {
  private val name = "name"
  private val description = "description"
  sealed case class Item(id: Int)(val name: String, val description: String, val icon: String = "") extends Block.Item
  object Item {
    private val counter = new AtomicInteger(0)
    def apply(name: String, description: String) = new Item(counter.getAndIncrement)(name, description)
    def apply(name: String, description: String, icon: String) = new Item(counter.getAndIncrement)(name, description, icon)
  }
  class Adapter(context: Activity, textViewResourceId: Int, data: Seq[Item])
    extends ArrayAdapter(context, textViewResourceId, android.R.id.text1, data.toArray) {
    private var inflater: LayoutInflater = context.getLayoutInflater
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
