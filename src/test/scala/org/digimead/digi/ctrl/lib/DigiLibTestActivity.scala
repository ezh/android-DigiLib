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

package org.digimead.digi.ctrl.lib

import scala.annotation.implicitNotFound

import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.log.Logging

import android.app.Activity
import android.os.Bundle

class DigiLibTestActivity extends Activity with DActivity with Logging {
  implicit val dispatcher = org.digimead.digi.ctrl.lib.DigiLibTestDispatcher.dispatcher
  @Loggable
  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    onCreateExt(this)
  }
  @Loggable
  override def onStart() {
    super.onStart()
    onStartExt(this, super.registerReceiver)
  }
  @Loggable
  override def onResume() = {
    super.onResume()
    onResumeExt(this)
  }
  @Loggable
  override def onPause() {
    onPauseExt(this)
    super.onPause()
  }
  @Loggable
  override def onStop() {
    onStopExt(this, false, super.unregisterReceiver)
    super.onStop()
  }
  @Loggable
  override def onDestroy() {
    onDestroyExt(this)
    super.onDestroy()
  }
}