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
import org.slf4j.LoggerFactory

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface

object FailedMarket {
  private val log = LoggerFactory.getLogger(getClass.getName().replaceFirst("org.digimead.digi.inetd", "o.d.d.i"))
  def getId(context: Context) = context.getResources().getIdentifier("dialog_FailedMarket", "id", context.getPackageName())
  @Loggable
  def createDialog(activity: Activity): Dialog = {
    log.debug("createDialog(...)")
    new AlertDialog.Builder(activity).
      setTitle(Android.getString(activity, "error_market_failed_title")).
      setMessage(Android.getString(activity, "error_market_failed_content")).
      setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
        @Loggable
        def onClick(dialog: DialogInterface, which: Int) {}
      }).
      create()
  }
}