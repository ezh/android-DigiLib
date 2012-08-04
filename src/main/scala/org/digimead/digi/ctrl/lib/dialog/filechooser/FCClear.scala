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

package org.digimead.digi.ctrl.lib.dialog.filechooser

import scala.ref.WeakReference

import org.digimead.digi.ctrl.lib.androidext.XResource
import org.digimead.digi.ctrl.lib.dialog.FileChooser

import android.view.View
import android.widget.Button

trait FCClear {
  this: FileChooser =>
  private lazy val clear = new WeakReference(extContent.map(l => l.findViewById(XResource.getId(l.getContext,
    "filechooser_clear")).asInstanceOf[Button]).getOrElse(null))

  def initializeClear() = clear.get.foreach {
    clear =>
      log.debug("FCClear::initializeClear")
      clear.setVisibility(View.GONE)
      clear.setOnClickListener(new View.OnClickListener() {
        override def onClick(v: View) {
          /*        copiedFiles.clear()
        cutFiles.clear()
        paste.setVisibility(View.GONE)
        clear.setVisibility(View.GONE)
        ActionUtils.displayMessage(FileChooserActivity.this, R.string.action_clear_success)*/
        }
      })
  }
}