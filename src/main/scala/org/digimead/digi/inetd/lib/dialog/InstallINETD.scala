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

package org.digimead.digi.inetd.lib.dialog

import org.digimead.digi.inetd.lib.aop.Loggable
import org.digimead.digi.inetd.lib.Android
import org.digimead.digi.inetd.lib.AppActivity
import org.digimead.digi.inetd.lib.Common
import org.digimead.digi.inetd.IINETDHost
import org.slf4j.LoggerFactory

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri

object InstallINETD {
  private val log = LoggerFactory.getLogger(getClass.getName().replaceFirst("org.digimead.digi.inetd", "o.d.d.i"))
  def getId(context: Context) = context.getResources().getIdentifier("dialog_InstallINETD", "id", context.getPackageName())
  @Loggable
  def createDialog(activity: Activity): Dialog = {
    log.debug("createDialog(...)")
    // check whether the intent can be resolved. If not, we will see
    // whether we can download it from the Market.
    val intent = new Intent(activity, classOf[IINETDHost])
    val packagename = intent.getPackage()
    val yesString = if (activity.getPackageManager().resolveActivity(intent, 0) == null) {
      // install
      log.info("install " + Common.Constant.marketPackage)
      Android.getString(activity, "install")
    } else {
      // reinstall
      log.info("reinstall " + Common.Constant.marketPackage)
      Android.getString(activity, "reinstall")
    }
    new AlertDialog.Builder(activity).
      setTitle(Android.getString(activity, "error_inetd_notfound_title")).
      setMessage(Android.getString(activity, "error_inetd_notfound_content")).
      setPositiveButton(yesString, new DialogInterface.OnClickListener() {
        @Loggable
        def onClick(dialog: DialogInterface, whichButton: Int) {
          log.info("install digiINETD from market")
          try {
            val intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=pname:" + Common.Constant.marketPackage))
            intent.addCategory(Intent.CATEGORY_BROWSABLE)
            activity.startActivity(intent)
          } catch {
            case _ => activity.showDialog(FailedMarket.getId(activity))
          }
        }
      }).
      setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
        @Loggable
        def onClick(dialog: DialogInterface, whichButton: Int) {
          log.info("copy path of prepared files to clipboard")
          AppActivity.Context.foreach(ctx => Common.copyPreparedFilesToClipboard(ctx))
        }
      }).
      create()
  }
}
