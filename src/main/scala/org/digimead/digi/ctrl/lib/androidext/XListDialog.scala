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

import scala.Option.option2Iterable

import org.digimead.digi.ctrl.lib.R
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.log.Logging

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView

trait XListDialog extends XAlertDialog {
  protected val adapter: BaseAdapter
  final val extContent = None
  override protected lazy val (cachedModal,
    modalContent,
    modalCustomContent,
    modalNegative,
    modalNeutral,
    modalPositive) =
    XListDialog.buildModal(getDialogActivity, title, message, extContent, icon, positive, neutral, negative, tag)
  override protected lazy val (cachedEmbedded,
    embeddedContent,
    embeddedCustomContent,
    embeddedNegative,
    embeddedNeutral,
    embeddedPositive) =
    XListDialog.buildEmbedded(getDialogActivity, title, message, extContent, icon, positive, neutral, negative, R.layout.fragment_dialog_list, tag)
  override protected lazy val cachedEmbeddedAttr = XResource.getAttributeSet(getDialogActivity, R.layout.fragment_dialog_list)

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    log.debug("XListDialog::onCreateView")
    val result = super.onCreateView(inflater, container, savedInstanceState)
    if (!getShowsDialog) {
      val result = embeddedCustomContent.map {
        case list: ListView =>
          modalCustomContent.map {
            case list: ListView =>
              if (list.getAdapter != null)
                log.debug("reset adapter " + list.getAdapter + " in modal ListView")
              list.setAdapter(null)
            case view =>
              log.fatal("unexpected ListView in modalCustomContent " + view + " for " + tag)
          }
          if (list.getAdapter != adapter)
            list.setAdapter(adapter)
        case view =>
          log.fatal("unexpected ListView in embeddedCustomContent " + view + " for " + tag)
      }
      result.getOrElse { log.fatal("ListView not found for " + tag) }
    }
    result
  }
  override def onCreateDialog(savedInstanceState: Bundle): Dialog = {
    log.debug("XListDialog::onCreateDialog")
    val dialog = super.onCreateDialog(savedInstanceState)
    val result = modalCustomContent.map {
      case list: ListView =>
        embeddedCustomContent.map {
          case list: ListView =>
            if (list.getAdapter != null)
              log.debug("reset adapter " + list.getAdapter + " in embedded ListView")
            list.setAdapter(null)
          case view =>
            log.fatal("unexpected ListView in embeddedCustomContent " + view + " for " + tag)
        }
        if (list.getAdapter != adapter)
          list.setAdapter(adapter)
      case view =>
        log.fatal("unexpected ListView in modalCustomContent " + view + " for " + tag)
    }
    result.getOrElse { log.fatal("ListView not found for " + tag) }
    dialog
  }
}

object XListDialog extends Logging {
  protected[androidext] def buildModal(context: Context, title: CharSequence,
    message: Option[CharSequence], extContent: Option[View], icon: Option[Int],
    positive: Option[(Int, XDialog.ButtonListener[_ <: XDialog])],
    neutral: Option[(Int, XDialog.ButtonListener[_ <: XDialog])],
    negative: Option[(Int, XDialog.ButtonListener[_ <: XDialog])],
    tag: String): (AlertDialog, Option[TextView], Option[View], Option[Button], Option[Button], Option[Button]) = {
    log.debug("XListDialog::buildModal for " + tag)
    val scale = context.getResources().getDisplayMetrics().density
    val padding = (10 * scale).toInt
    val builder = new AlertDialog.Builder(context).setTitle(title)
    val customContentView = {
      val extContentContainer = new LinearLayout(context)
      val extContent = new ListView(context)
      extContentContainer.addView(extContent, new ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
      extContentContainer.setPadding(padding, padding, padding, padding)
      builder.setView(extContentContainer)
      extContent
    }
    icon.foreach(builder.setIcon)
    negative.foreach(t => builder.setNegativeButton(t._1, t._2))
    neutral.foreach(t => builder.setNeutralButton(t._1, t._2))
    positive.foreach(t => builder.setPositiveButton(t._1, t._2))

    val contentView = {
      val container = customContentView.getParent.asInstanceOf[ViewGroup]
      val contentView = new TextView(context)
      contentView.setTextAppearance(context, android.R.style.TextAppearance_Medium)
      contentView.setPadding(0, 0, 0, padding)
      container.addView(contentView, 0, new ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
      contentView
    }
    val dialog = builder.create()
    /*
     * ABSOLUTELY CRAZY BEHAVIOR, emulator, API 10
     * without dialog.show most of the time (sometimes, rarely not)
     * 
     * android.util.AndroidRuntimeException: requestFeature() must be called before adding content
     * at com.android.internal.policy.impl.PhoneWindow.requestFeature(PhoneWindow.java:181)
     * at com.android.internal.app.AlertController.installContent(AlertController.java:199)
     * at android.app.AlertDialog.onCreate(AlertDialog.java:251)
     * at android.app.Dialog.dispatchOnCreate(Dialog.java:307)
     * at android.app.Dialog.show(Dialog.java:225)
     * at android.support.v4.app.DialogFragment.onStart(DialogFragment.java:385)
     */
    dialog.show
    dialog.hide
    val negativeView = negative.flatMap(n => Option(dialog.getButton(0)))
    val neutralView = neutral.flatMap(n => Option(dialog.getButton(1)))
    val positiveView = positive.flatMap(n => Option(dialog.getButton(2)))
    (dialog, Some(contentView), Some(customContentView), negativeView, neutralView, positiveView)
  }
  protected def buildEmbedded(context: Context, title: CharSequence,
    message: Option[CharSequence], extContent: Option[View], icon: Option[Int],
    positive: Option[(Int, XDialog.ButtonListener[_ <: XDialog])],
    neutral: Option[(Int, XDialog.ButtonListener[_ <: XDialog])],
    negative: Option[(Int, XDialog.ButtonListener[_ <: XDialog])],
    baseView: Int, tag: String): (View, Option[TextView], Option[View], Option[Button], Option[Button], Option[Button]) = {
    log.debug("XListDialog::buildEmbedded for " + tag)
    def setButtonListener(bView: Button, title: Int, callback: XDialog.ButtonListener[_ <: XDialog], whichButton: Int) {
      bView.setVisibility(View.VISIBLE)
      bView.setText(title)
      bView.setOnClickListener(new View.OnClickListener { def onClick(v: View) = callback.onClick(null, whichButton) })
    }
    val view = LayoutInflater.from(context).inflate(baseView, null)
    val contentView = view.findViewById(android.R.id.content).asInstanceOf[TextView]
    val titleView = view.findViewById(android.R.id.title).asInstanceOf[TextView]
    titleView.setText(title)
    icon.foreach {
      icon =>
        val iconContainer = view.findViewById(android.R.id.icon).asInstanceOf[ImageView]
        iconContainer.setImageResource(icon)
        iconContainer.setVisibility(View.VISIBLE)
    }
    val customContentView = Option(view.findViewById(android.R.id.custom).asInstanceOf[ListView])
    message match {
      case Some(message) =>
        contentView.setText(message)
      case None =>
        contentView.setVisibility(View.GONE)
    }
    if (negative.nonEmpty || neutral.nonEmpty || positive.nonEmpty)
      view.findViewById(android.R.id.summary).setVisibility(View.VISIBLE)
    val negativeView = negative.map(t => {
      val buttonView = view.findViewById(android.R.id.button1).asInstanceOf[Button]
      setButtonListener(buttonView, t._1, t._2, DialogInterface.BUTTON_NEGATIVE)
      buttonView
    })
    val neutralView = neutral.map(t => {
      val buttonView = view.findViewById(android.R.id.button2).asInstanceOf[Button]
      setButtonListener(buttonView, t._1, t._2, DialogInterface.BUTTON_NEUTRAL)
      buttonView
    })
    val positiveView = positive.map(t => {
      val buttonView = view.findViewById(android.R.id.button3).asInstanceOf[Button]
      setButtonListener(buttonView, t._1, t._2, DialogInterface.BUTTON_POSITIVE)
      buttonView
    })
    (view, Some(contentView), customContentView, negativeView, neutralView, positiveView)
  }
}
