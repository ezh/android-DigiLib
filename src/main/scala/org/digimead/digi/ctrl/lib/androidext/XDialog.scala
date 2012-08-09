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
import scala.ref.WeakReference

import org.digimead.digi.ctrl.lib.R
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.base.AppComponent
import org.digimead.digi.ctrl.lib.log.Logging

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.support.v4.app.FragmentActivity
import android.support.v4.app.FragmentManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView

trait XDialog extends DialogFragment with SafeDialog with Logging {
  protected lazy val defaultButtonCallback: (XDialog => Any) =
    (dialog) => if (!dialog.getShowsDialog)
      dialog.getDialogActivity.getSupportFragmentManager.
        popBackStackImmediate(dialog.tag, FragmentManager.POP_BACK_STACK_INCLUSIVE)

  def getDialogActivity(): FragmentActivity
  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    log.debug("XDialog::onCreateView")
    val view = super.onCreateView(inflater, container, savedInstanceState)
    notifyBefore
    view
  }
  override def onCreateDialog(savedInstanceState: Bundle): Dialog = {
    log.debug("XDialog::onCreateDialog")
    val dialog = super.onCreateDialog(savedInstanceState)
    notifyBefore
    dialog
  }
  override def onResume() = {
    log.debug("XDialog::onResume")
    super.onResume
  }
  override def onPause() = {
    log.debug("XDialog::onPause")
    /*
     * android fragment transaction architecture is incomplete
     * backstack persistency is unfinished
     * reimplement it by hands is unreasonable
     * deadlines, beer, other stuff... we must to forgive android framework coders
     * in the hope of android 5.x
     * 30.07.2012 Ezh
     */
    // main activity unavailable (configuration change are in progress for example)
    if (!SafeDialog.isEnabled)
      getDialogActivity.getSupportFragmentManager.
        popBackStack(this.toString, FragmentManager.POP_BACK_STACK_INCLUSIVE)
    super.onPause
  }
  override def onDestroyView() {
    log.debug("XDialog::onDestroyView")
    notifyAfter
    super.onDestroyView
  }
  override def onDismiss(dialog: DialogInterface) {
    log.debug("XDialog::onDismiss")
    super.onDismiss(dialog)
    notifySafeDialogDismissed(dialog)
    notifyAfter
  }
  @Loggable
  def isInBackStack(manager: FragmentManager): Boolean = {
    val tag = toString
    if (manager.findFragmentByTag(tag) == null)
      return false
    for (i <- 0 until manager.getBackStackEntryCount)
      if (manager.getBackStackEntryAt(i).getName == tag)
        return true
    false
  }
}

object XDialog {
  implicit def dialog2string(d: XDialog) = d.tag

  class ButtonListener[T <: XDialog](dialog: WeakReference[T],
    callback: Option[(T) => Any]) extends DialogInterface.OnClickListener() with Logging {
    @Loggable
    def onClick(dialogInterface: DialogInterface, whichButton: Int) = try {
      for {
        dialog <- dialog.get
        callback <- callback
      } callback(dialog)
    } catch {
      case e =>
        log.error(e.getMessage, e)
    }
  }
}
