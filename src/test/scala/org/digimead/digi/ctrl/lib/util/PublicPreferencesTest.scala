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

package org.digimead.digi.ctrl.lib.util

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicReference

import org.digimead.digi.ctrl.lib.DigiLibTestActivity
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.log.AndroidLogger
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.log.LoggingEvent
import org.scalatest.junit.JUnitSuite
import org.scalatest.junit.ShouldMatchersForJUnit

import com.jayway.android.robotium.solo.Solo

import android.content.SharedPreferences
import android.test.ActivityInstrumentationTestCase2

class PublicPreferencesTest
  extends ActivityInstrumentationTestCase2[DigiLibTestActivity]("org.digimead.digi.ctrl.lib", classOf[DigiLibTestActivity])
  with JUnitSuite with ShouldMatchersForJUnit {
  @volatile private var solo: Solo = null
  @volatile private var activity: DigiLibTestActivity = null
  val logResult = new SyncVar[Boolean]()
  val logSubscriber = new LogSubscriber

  def assertLog(s: String, f: (String, String) => Boolean, timeout: Long) = synchronized {
    logResult.unset()
    logSubscriber.want.set(s, f)
    Logging.resume
    assert(logResult.get(timeout) == Some(true), "log record \"" + s + "\" not found")
    logSubscriber.want.set(null)
  }
  def deleteFile(dfile: File): Unit = {
    if (dfile.isDirectory)
      dfile.listFiles.foreach { f => deleteFile(f) }
    dfile.delete
  }

  override def setUp() {
    super.setUp()
    Logging.reset(true)
    activity = getActivity
    solo = new Solo(getInstrumentation(), activity)
    activity.log.info("setUp")
    Logging.addLogger(AndroidLogger)
    Logging.subscribe(logSubscriber)
  }
  override def tearDown() = {
    activity.log.info("tearDown")
    try {
      solo.finalize()
    } catch {
      case e =>
        e.printStackTrace()
    }
    activity.finish()
    Logging.removeSubscriptions
    super.tearDown()
    Thread.sleep(1000)
  }
  def testCore() {
    activity.log.info("testCore BEGIN")

    val location = PublicPreferences.getLocation(activity)
    location should not be ('empty)
    deleteFile(location.get)
    location.get.exists should be(false)

    Logging.suspend
    val preference = PublicPreferences(activity)
    assertLog("create new public preferences id ", _.startsWith(_), 10000)

    val blobID = preference.blob.get

    assert(preference.blob.get > 0, "incorrect blob id")

    new File(preference.file, preference.blob.get + ".blob").exists should be(false)

    preference.getAll should be('empty)

    val editor = preference.edit.asInstanceOf[PublicPreferences.Editor]

    editor.changes should be('empty)

    editor.putBoolean("test", true)

    preference.blob.get should equal(blobID)

    Logging.suspend
    editor.commit()
    assertLog("writeToParcel PublicPreferences with flags 0", _ == _, 10000)

    preference.file should be('directory)

    new File(preference.file, preference.blob.get + ".blob") should not have length(0)

    preference.blob.get should be > (blobID)

    val preference2 = PublicPreferences(activity)

    preference2 should equal(preference)

    activity.log.info("testCore END")
  }
  def testPublishSubscribe() {
    activity.log.info("testPublishSubscribe BEGIN")

    val prefResult = new SyncVar[(Long, String, Boolean)]()

    PublicPreferences.unregisterOnSharedPreferenceChangeListeners
    val preference = PublicPreferences(activity)

    Logging.suspend
    preference.registerOnSharedPreferenceChangeListener(new SharedPreferences.OnSharedPreferenceChangeListener {
      def onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) =
        prefResult.set((sharedPreferences.asInstanceOf[PublicPreferences].blob.get, key, sharedPreferences.getBoolean(key, false)))
    })
    assertLog("register OnSharedPreferenceChangeListener entry point", _ == _, 10000)

    val editor = preference.edit
    val key = "test_testPublishSubscribe" + System.currentTimeMillis
    editor.putBoolean(key, true)
    editor.commit

    preference.contains(key) should be(true)

    preference.getBoolean(key, false) should be(true)

    val newPreferece = prefResult.get(10000)

    newPreferece should not be ('empty)

    newPreferece.get._1 should equal(preference.blob.get)

    newPreferece.get._2 should equal(key)

    newPreferece.get._3 should equal(true)

    PublicPreferences.unregisterOnSharedPreferenceChangeListeners

    activity.log.info("testPublishSubscribe END")
  }
  def testLatest() {
    activity.log.info("testLatest BEGIN")

    val location = PublicPreferences.getLocation(activity)
    location should not be ('empty)
    deleteFile(location.get)
    location.get.exists should be(false)

    val preference = PublicPreferences(activity)
    val edit = preference.edit()
    edit.putBoolean("abc", true)
    edit.commit
    val blob = preference.blob.get

    assert(preference.blob.get > 0, "incorrect blob id")

    val currentFile = new File(preference.file, preference.blob.get + ".blob")
    val latestFile = new File(preference.file, Long.MaxValue + ".blob")
    val in = new FileInputStream(currentFile)
    val out = new FileOutputStream(latestFile)
    Common.writeToStream(in, out)
    in.close()
    out.close()

    val preference2 = PublicPreferences(activity)

    preference2.blob.get should be === (Long.MaxValue)

    activity.log.info("testLatest END")
  }
  class LogSubscriber extends Logging.Sub {
    val want = new AtomicReference[(String, (String, String) => Boolean)](null)
    def notify(pub: Logging.type#Pub, event: LoggingEvent) = event match {
      case event: Logging.Record =>
        if (want.get != null && want.get._2(event.message, want.get._1))
          logResult.put(true, 0)
      case _ =>
    }
  }
}