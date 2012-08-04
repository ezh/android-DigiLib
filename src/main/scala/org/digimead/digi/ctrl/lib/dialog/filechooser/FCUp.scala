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

import java.io.File

import scala.ref.WeakReference

import org.digimead.digi.ctrl.lib.androidext.XResource
import org.digimead.digi.ctrl.lib.dialog.FileChooser

import android.view.View
import android.widget.Button
import android.widget.Toast

trait FCUp {
  this: FileChooser =>
  private lazy val up = new WeakReference(extContent.map(l => l.findViewById(XResource.getId(l.getContext,
    "filechooser_up")).asInstanceOf[Button]).getOrElse(null))

  def initializeUp() = up.get.foreach {
    up =>
      log.debug("FCUp::initializeUp")
      up.setOnClickListener(new View.OnClickListener() {
        override def onClick(v: View) = {
          activeDirectory.getParentFile match {
            case parent: File =>
              //              Toast.makeText(v.getContext, XResource.getString(v.getContext, "filechooser_change_directory_to").
              //                getOrElse("change directory to %s").format(parent), Toast.LENGTH_SHORT).show()
              showDirectory(parent.getAbsoluteFile)
            case null =>
              Toast.makeText(v.getContext, XResource.getString(v.getContext, "filechooser_change_directory_to_failed").
                getOrElse("unable to change directory to %s").format("outer of " + activeDirectory), Toast.LENGTH_SHORT).show()
          }
        }
      })
  }
}