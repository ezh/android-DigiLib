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
import scala.ref.WeakReference

import org.digimead.digi.ctrl.lib.androidext.XResource
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.base.AppComponent
import org.digimead.digi.ctrl.lib.declaration.DTimeout
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.message.Dispatcher
import org.digimead.digi.ctrl.lib.message.IAmWarn
import org.digimead.digi.ctrl.lib.message.IAmYell

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
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView

class CommunityBlock(val context: Context,
  val xdaUri: Option[Future[Uri]],
  val wikiUri: Option[Future[Uri]],
  val translationUri: Option[Future[Uri]],
  val translationCommonUri: Option[Future[Uri]])(implicit val dispatcher: Dispatcher) extends Block[CommunityBlock.Item] with Logging {
  val itemXDA = CommunityBlock.Item(XResource.getString(context, "block_community_xda_title").getOrElse("XDA developers community"),
    XResource.getString(context, "block_community_xda_description").getOrElse("XDA forum thread"), "ic_block_community_xda_logo")
  val itemWiki = CommunityBlock.Item(XResource.getString(context, "block_community_wiki_title").getOrElse("wiki"),
    XResource.getString(context, "block_community_wiki_description").getOrElse("collaborate on a documentation"), "ic_block_community_wiki")
  val itemTranslation = CommunityBlock.Item(XResource.getString(context, "block_community_translate_title").getOrElse("translation of %s").
    format(XResource.getString(context, "app_name").getOrElse("Unknown")),
    XResource.getString(context, "block_community_translate_description").getOrElse("your help with translation are very appreciated"), "ic_block_community_translate")
  val itemTranslationCommon = CommunityBlock.Item(XResource.getString(context, "block_community_translate_title").getOrElse("translation of %s").format("DigiLib"),
    XResource.getString(context, "block_community_translate_description").getOrElse("your help with translation are very appreciated"), "ic_block_community_translate")
  val items = Seq() ++ (if (xdaUri != None) Seq(itemXDA) else Seq()) ++ (if (wikiUri != None) Seq(itemWiki) else Seq()) ++
    (if (translationUri != None) Seq(itemTranslation) else Seq()) ++ (if (translationCommonUri != None) Seq(itemTranslationCommon) else Seq())
  private lazy val header = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater].
    inflate(XResource.getId(context, "header", "layout"), null).asInstanceOf[TextView]
  private lazy val adapter = new CommunityBlock.Adapter(context, XResource.getId(context, "block_list_item", "layout"), items)
  @Loggable
  def appendTo(mergeAdapter: MergeAdapter) = {
    log.debug("append " + getClass.getName + " to MergeAdapter")
    header.setText(Html.fromHtml(XResource.getString(context, "block_community_title").getOrElse("community")))
    mergeAdapter.addView(header)
    mergeAdapter.addAdapter(adapter)
  }
  @Loggable
  def onListItemClick(l: ListView, v: View, item: CommunityBlock.Item) = {
    item match {
      case this.itemXDA => // jump to XDA
        Futures.future {
          xdaUri.foreach(future => Futures.awaitAll(CommunityBlock.retriveTimeout, future).asInstanceOf[List[Option[Uri]]] match {
            case List(Some(xdaUri)) =>
              log.debug("open XDA page at " + xdaUri)
              try {
                val intent = new Intent(Intent.ACTION_VIEW, xdaUri)
                intent.addCategory(Intent.CATEGORY_BROWSABLE)
                AppComponent.Context match {
                  case Some(activity) if activity.isInstanceOf[Activity] => activity.startActivity(intent)
                  case _ => context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
              } catch {
                case e =>
                  IAmYell("Unable to open XDA thread page: " + xdaUri, e)
              }
            case _ =>
              IAmWarn("Unable to get XDA thread page information, timeout")
          })
        }
      case this.itemWiki => // jump to project
        Futures.future {
          wikiUri.foreach(future => Futures.awaitAll(CommunityBlock.retriveTimeout, future).asInstanceOf[List[Option[Uri]]] match {
            case List(Some(wikiUri)) =>
              log.debug("open wiki page at " + wikiUri)
              try {
                val intent = new Intent(Intent.ACTION_VIEW, wikiUri)
                intent.addCategory(Intent.CATEGORY_BROWSABLE)
                AppComponent.Context match {
                  case Some(activity) if activity.isInstanceOf[Activity] => activity.startActivity(intent)
                  case _ => context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
              } catch {
                case e =>
                  IAmYell("Unable to open wiki page: " + wikiUri, e)
              }
            case _ =>
              IAmWarn("Unable to get wiki page information, timeout")
          })
        }
      case this.itemTranslation => // jump to translation
        Futures.future {
          translationUri.foreach(future => Futures.awaitAll(CommunityBlock.retriveTimeout, future).asInstanceOf[List[Option[Uri]]] match {
            case List(Some(translationUri)) =>
              log.debug("open translation page at " + translationUri)
              try {
                val intent = new Intent(Intent.ACTION_VIEW, translationUri)
                intent.addCategory(Intent.CATEGORY_BROWSABLE)
                AppComponent.Context match {
                  case Some(activity) if activity.isInstanceOf[Activity] => activity.startActivity(intent)
                  case _ => context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
              } catch {
                case e =>
                  IAmYell("Unable to open translation page: " + translationUri, e)
              }
            case _ =>
              IAmWarn("Unable to get translation page information, timeout")
          })
        }
      case this.itemTranslationCommon => // jump to common translation
        Futures.future {
          translationCommonUri.foreach(future => Futures.awaitAll(CommunityBlock.retriveTimeout, future).asInstanceOf[List[Option[Uri]]] match {
            case List(Some(translationCommonUri)) =>
              log.debug("open common translation page at " + translationCommonUri)
              try {
                val intent = new Intent(Intent.ACTION_VIEW, translationCommonUri)
                intent.addCategory(Intent.CATEGORY_BROWSABLE)
                AppComponent.Context match {
                  case Some(activity) if activity.isInstanceOf[Activity] => activity.startActivity(intent)
                  case _ => context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
              } catch {
                case e =>
                  IAmYell("Unable to open common translation page: " + translationCommonUri, e)
              }
            case _ =>
              IAmWarn("Unable to get common translation page information, timeout")
          })
        }
    }
  }
  @Loggable
  override def onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo, item: CommunityBlock.Item) {
    log.debug("create context menu for " + item.name)
    menu.setHeaderTitle(item.name)
    if (item.icon.nonEmpty)
      XResource.getId(context, item.icon, "drawable") match {
        case i if i != 0 =>
          menu.setHeaderIcon(i)
        case _ =>
      }
    item match {
      case this.itemXDA =>
        menu.add(Menu.NONE, XResource.getId(context, "block_link_copy"), 1,
          XResource.getString(context, "block_link_copy").getOrElse("Copy link"))
        menu.add(Menu.NONE, XResource.getId(context, "block_link_send"), 2,
          XResource.getString(context, "block_link_send").getOrElse("Send link to ..."))
      case this.itemWiki =>
        menu.add(Menu.NONE, XResource.getId(context, "block_link_copy"), 1,
          XResource.getString(context, "block_link_copy").getOrElse("Copy link"))
        menu.add(Menu.NONE, XResource.getId(context, "block_link_send"), 2,
          XResource.getString(context, "block_link_send").getOrElse("Send link to ..."))
      case this.itemTranslation =>
        menu.add(Menu.NONE, XResource.getId(context, "block_link_copy"), 1,
          XResource.getString(context, "block_link_copy").getOrElse("Copy link"))
        menu.add(Menu.NONE, XResource.getId(context, "block_link_send"), 2,
          XResource.getString(context, "block_link_send").getOrElse("Send link to ..."))
      case this.itemTranslationCommon =>
        menu.add(Menu.NONE, XResource.getId(context, "block_link_copy"), 1,
          XResource.getString(context, "block_link_copy").getOrElse("Copy link"))
        menu.add(Menu.NONE, XResource.getId(context, "block_link_send"), 2,
          XResource.getString(context, "block_link_send").getOrElse("Send link to ..."))
      case item =>
        log.fatal("unsupported context menu item " + item)
    }
  }
  @Loggable
  override def onContextItemSelected(menuItem: MenuItem, item: CommunityBlock.Item): Boolean = {
    item match {
      case this.itemXDA =>
        Futures.future {
          xdaUri.map(future => Futures.awaitAll(CommunityBlock.retriveTimeout, future).asInstanceOf[List[Option[Uri]]] match {
            case List(Some(xdaUri)) =>
              menuItem.getItemId match {
                case id if id == XResource.getId(context, "block_link_copy") =>
                  Block.copyLink(context, item, xdaUri.toString)
                case id if id == XResource.getId(context, "block_link_send") =>
                  Block.sendLink(context, item, item.name, xdaUri.toString)
                case message =>
                  log.fatal("skip unknown message " + message)
              }
            case _ =>
              IAmWarn("Unable to get XDA thread page information, timeout")
          })
        }
        true
      case this.itemWiki =>
        Futures.future {
          wikiUri.map(future => Futures.awaitAll(CommunityBlock.retriveTimeout, future).asInstanceOf[List[Option[Uri]]] match {
            case List(Some(wikiUri)) =>
              menuItem.getItemId match {
                case id if id == XResource.getId(context, "block_link_copy") =>
                  Block.copyLink(context, item, wikiUri.toString)
                case id if id == XResource.getId(context, "block_link_send") =>
                  Block.sendLink(context, item, item.name, wikiUri.toString)
                case message =>
                  log.fatal("skip unknown message " + message)
              }
            case _ =>
              IAmWarn("Unable to get wiki page information, timeout")
          })
        }
        true
      case this.itemTranslation =>
        Futures.future {
          translationUri.map(future => Futures.awaitAll(CommunityBlock.retriveTimeout, future).asInstanceOf[List[Option[Uri]]] match {
            case List(Some(translationUri)) =>
              menuItem.getItemId match {
                case id if id == XResource.getId(context, "block_link_copy") =>
                  Block.copyLink(context, item, translationUri.toString)
                case id if id == XResource.getId(context, "block_link_send") =>
                  Block.sendLink(context, item, item.name, translationUri.toString)
                case message =>
                  log.fatal("skip unknown message " + message)
                  false
              }
            case _ =>
              IAmWarn("Unable to get translation page information, timeout")
          })
        }
        true
      case this.itemTranslationCommon =>
        Futures.future {
          translationCommonUri.map(future => Futures.awaitAll(CommunityBlock.retriveTimeout, future).asInstanceOf[List[Option[Uri]]] match {
            case List(Some(translationCommonUri)) =>
              menuItem.getItemId match {
                case id if id == XResource.getId(context, "block_link_copy") =>
                  Block.copyLink(context, item, translationCommonUri.toString)
                case id if id == XResource.getId(context, "block_link_send") =>
                  Block.sendLink(context, item, item.name, translationCommonUri.toString)
                case message =>
                  log.fatal("skip unknown message " + message)
              }
            case _ =>
              IAmWarn("Unable to get common translation page information, timeout")
          })
        }
        true
      case item =>
        log.fatal("unsupported context menu item " + item)
        false
    }
  }
}

object CommunityBlock {
  private val retriveTimeout = DTimeout.normal
  private val name = "name"
  private val description = "description"
  case class Item(name: String, description: String, icon: String = "") extends Block.Item
  class Adapter(context: Context, textViewResourceId: Int, data: Seq[Item])
    extends ArrayAdapter(context, textViewResourceId, android.R.id.text1, data.toArray) {
    private val inflater: LayoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater]
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
            XResource.getId(context, item.icon, "drawable") match {
              case i if i != 0 =>
                icon.setVisibility(View.VISIBLE)
                icon.setImageDrawable(context.getResources.getDrawable(i))
              case _ =>
            }
          Level.novice(view)
          item.view = new WeakReference(view)
          view
        case Some(view) =>
          view
      }
    }
  }
}
