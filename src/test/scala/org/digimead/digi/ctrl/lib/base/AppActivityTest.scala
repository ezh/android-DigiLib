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

package org.digimead.digi.ctrl.lib.base

import org.digimead.digi.ctrl.lib.log.ConsoleLogger
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.Activity
import org.digimead.RobotEsTrick
import org.scalatest.BeforeAndAfter
import org.scalatest.FunSuite
import org.scalatest.matchers.ShouldMatchers._

class AppActivityTestOnPrepareEnvironment_j1 extends FunSuite with BeforeAndAfter with RobotEsTrick {
  lazy val roboClassHandler = RobotEsTrick.classHandler
  lazy val roboClassLoader = RobotEsTrick.classLoader
  lazy val roboDelegateLoadingClasses = RobotEsTrick.delegateLoadingClasses
  lazy val roboConfig = RobotEsTrick.config

  before {
    roboSetup
  }

  test("check environment version") {
    val activity = new android.app.Activity with Activity
    Logging.addLogger(ConsoleLogger)
    activity.onCreate(null)
    val nativeManifest = <manifest>
                           <build>
                             <name>digiControl</name>
                             <vendor>org.digimead</vendor>
                             <version>0.0.1-20120314.122628</version>
                           </build>
                           <application>
                             <name>bridge</name>
                             <version>0.0.1.1</version>
                             <project>http://github.com/ezh/android-DigiControl</project>
                             <description>helper of the higher ground</description>
                             <license>Proprietary</license>
                             <origin>Copyright © 2011-2012 Alexey B. Aksenov/Ezh. All rights reserved.</origin>
                           </application>
                         </manifest>
    val nativeManifestInstalled = <manifest>
                                    <build>
                                      <name>digiControl</name>
                                      <vendor>org.digimead</vendor>
                                      <version>0.0.1-20120314.122629</version>
                                    </build>
                                    <application>
                                      <name>bridge</name>
                                      <version>0.0.1.1</version>
                                      <project>http://github.com/ezh/android-DigiControl</project>
                                      <description>helper of the higher ground</description>
                                      <license>Proprietary</license>
                                      <origin>Copyright © 2011-2012 Alexey B. Aksenov/Ezh. All rights reserved.</origin>
                                    </application>
                                  </manifest>
    AppActivity.Inner.checkEnvironmentVersion(Some(nativeManifest), Some(nativeManifestInstalled)) should be(true)
    Logging.flush
  }
}
