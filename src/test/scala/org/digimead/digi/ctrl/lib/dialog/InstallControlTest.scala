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

import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.RobotEsTrick
import org.scalatest.matchers.ShouldMatchers._
import org.scalatest.BeforeAndAfter
import org.scalatest.FunSuite

import com.xtremelabs.robolectric.shadows.ShadowAlertDialog
import com.xtremelabs.robolectric.Robolectric

import android.app.Activity

class InstallControlTest extends FunSuite with BeforeAndAfter with RobotEsTrick {
  lazy val roboClassHandler = RobotEsTrick.classHandler
  lazy val roboClassLoader = RobotEsTrick.classLoader
  lazy val roboDelegateLoadingClasses = RobotEsTrick.delegateLoadingClasses
  lazy val roboConfig = RobotEsTrick.config
  override val debug = true

  before {
    roboSetup
  }

  test("test InstallControl dialog") {
    val activity = new Activity()
    InstallControl.getId(activity) should (not equal (null) and not be (0) and not be (-1))
    val dialog = InstallControl.createDialog(activity)
    dialog.show()
    val lastDialog = ShadowAlertDialog.getLatestAlertDialog()
    lastDialog should be === dialog
    val shadow = Robolectric.shadowOf(lastDialog)
    shadow.getTitle() should be === "DigiControl not found"
    shadow.getMessage() should startWith("Install from market")
  }

}

