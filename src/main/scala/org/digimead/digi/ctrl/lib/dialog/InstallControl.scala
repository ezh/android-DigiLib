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

package org.digimead.digi.ctrl.lib.dialog

import scala.annotation.implicitNotFound

import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.base.AppComponent
import org.digimead.digi.ctrl.lib.declaration.DConstant
import org.digimead.digi.ctrl.lib.declaration.DIntent
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.log.RichLogger
import org.digimead.digi.ctrl.lib.message.Dispatcher
import org.digimead.digi.ctrl.lib.message.IAmMumble
import org.digimead.digi.ctrl.lib.util.Android
import org.digimead.digi.ctrl.lib.util.Version

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager.NameNotFoundException
import android.net.Uri
import android.text.Html

object InstallControl extends Logging {
  def getId(context: Context) = Android.getId(context, "install_digicontrol")
  @Loggable
  def createDialog(activity: Activity)(implicit logger: RichLogger, dispatcher: Dispatcher): Dialog = {
    // check whether the intent can be resolved. If not, we will see
    // whether we can download it from the Market.
    val intent = new Intent(DIntent.HostService)
    val packagename = intent.getPackage()
    val action =
      AppComponent.Inner.minVersionRequired(DConstant.controlPackage) match {
        case Some(minVersion) => try {
          val pm = activity.getPackageManager()
          val pi = pm.getPackageInfo(DConstant.controlPackage, 0)
          val version = new Version(pi.versionName)
          log.debug(DConstant.controlPackage + " minimum version '" + minVersion + "' and current version '" + version + "'")
          if (version.compareTo(minVersion) == -1)
            Android.getString(activity, "update").getOrElse("update")
          else
            Android.getString(activity, "reinstall").getOrElse("reinstall")
        } catch {
          case e: NameNotFoundException =>
            Android.getString(activity, "install").getOrElse("install")
        }
        case None => try {
          val pm = activity.getPackageManager()
          val pi = pm.getPackageInfo(DConstant.controlPackage, 0)
          Android.getString(activity, "update").getOrElse("update")
        } catch {
          case e: NameNotFoundException =>
            Android.getString(activity, "install").getOrElse("install")
        }
      }
    new AlertDialog.Builder(activity).
      setIcon(Android.getId(activity, "ic_control_icon", "drawable")).
      setTitle(Android.getString(activity, "error_digicontrol_not_found_title").
        getOrElse("DigiControl failed")).
      setMessage(Html.fromHtml(Android.getString(activity, "error_digicontrol_not_found_content").
        getOrElse("DigiControl application not found on the device").format(action))).
      setPositiveButton(action(0).toUpper + action.substring(1), new DialogInterface.OnClickListener() {
        @Loggable
        def onClick(dialog: DialogInterface, whichButton: Int) {
          IAmMumble("install DigiControl from market")(logger, dispatcher)
          try {
            val intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=pname:" + DConstant.controlPackage))
            intent.addCategory(Intent.CATEGORY_BROWSABLE)
            activity.startActivity(intent)
          } catch {
            case _ => AppComponent.Inner.showDialogSafe(activity, FailedMarket.getId(activity))
          }
        }
      }).
      setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
        @Loggable
        def onClick(dialog: DialogInterface, whichButton: Int) {
          IAmMumble("install DigiControl from market canceled")(logger, dispatcher)
        }
      }).
      create()
  }
}
