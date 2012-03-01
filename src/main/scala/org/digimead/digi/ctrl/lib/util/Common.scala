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

import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.OutputStream
import java.net.NetworkInterface

import scala.Array.canBuildFrom
import scala.Option.option2Iterable
import scala.annotation.tailrec
import scala.collection.JavaConversions._
import scala.collection.immutable.HashMap
import scala.concurrent.Lock

import org.digimead.digi.ctrl.lib.aop.RichLogger.rich2plain
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.aop.Logging
import org.digimead.digi.ctrl.lib.base.AppActivity
import org.digimead.digi.ctrl.lib.declaration.DConstant
import org.digimead.digi.ctrl.lib.declaration.DIntent
import org.digimead.digi.ctrl.lib.dialog.FailedMarket
import org.digimead.digi.ctrl.lib.dialog.InstallControl
import org.digimead.digi.ctrl.lib.Activity
import org.digimead.digi.ctrl.ICtrlComponent

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.text.ClipboardManager
import android.widget.Toast

object Common extends Logging {
  log.debug("alive")
  @Loggable
  def onCreateDialog(id: Int, activity: Activity) = id match {
    case id if id == InstallControl.getId(activity) =>
      InstallControl.createDialog(activity)
    case id if id == FailedMarket.getId(activity) =>
      FailedMarket.createDialog(activity)
    case _ =>
      log.fatal("unknown dialog id " + id)
      null
  }
  @Loggable
  def listInterfaces(): Seq[String] = {
    var interfaces = HashMap[String, Seq[String]]()
    try {
      val nie = NetworkInterface.getNetworkInterfaces()
      while (nie.hasMoreElements) {
        val ni = nie.nextElement
        val name = ni.getName()
        if (name != "lo") {
          interfaces = interfaces.updated(name, Seq())
          val iae = ni.getInetAddresses
          while (iae.hasMoreElements) {
            val ia = iae.nextElement
            val address = ia.getHostAddress
            // skip ipv6
            if (address.matches("""\d+\.\d+\.\d+.\d+""") && !address.endsWith("127.0.0.1"))
              interfaces = interfaces.updated(name, interfaces(name) :+ address)
          }
        }
      }
    } catch {
      case e =>
        // suspect permission error at one of interfaces ;-)
        log.warn("NetworkInterface.getNetworkInterfaces() failed with " + e +
          (if (e.getMessage() != null) " " + e.getMessage))
    }
    // convert hash interface -> address to string interface:address
    interfaces.keys.map(i => {
      if (interfaces(i).isEmpty) Seq(i + ":0.0.0.0") else interfaces(i).map(s => i + ":" + s)
    }).flatten.toSeq
  }
  @Loggable
  def listPreparedFiles(context: Context): Option[Seq[File]] = for {
    inner <- AppActivity.Inner
    appNativePath <- inner.appNativePath
  } yield context.getAssets.list(DConstant.apkNativePath).map(name => new File(appNativePath, name)).filter(_.exists)
  @Loggable
  def copyPreparedFilesToClipboard(context: Context) = {
    val files = Common.listPreparedFiles(context).mkString("\n")
    val text = if (files.nonEmpty) {
      val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE).asInstanceOf[ClipboardManager]
      // TODO
      clipboard.setText(files)
      Android.getString(context, "notify_n_files_copy_to_clipboard").map(_.format(files.size)).
        getOrElse("Copy files to clipboard")
    } else {
      Android.getString(context, "notify_no_files_copy_to_clipboard").
        getOrElse("There are no files to copy to clipboard")
    }
    Toast.makeText(context, text, DConstant.toastTimeout).show()
  }
  @Loggable
  def execChmod(permission: String, file: File, recursive: Boolean = false): Boolean = {
    val args = if (recursive)
      Array("/bin/chmod", permission, file.getAbsolutePath)
    else
      Array("/bin/chmod", "-R", permission, file.getAbsolutePath)
    val p = Runtime.getRuntime().exec(args)
    val err = new BufferedReader(new InputStreamReader(p.getErrorStream()))
    p.waitFor()
    val retcode = p.exitValue()
    if (retcode != 0) {
      var error = err.readLine()
      while (error != null) {
        log.fatal("/bin/chmod error: " + error)
        error = err.readLine()
      }
      false
    } else
      true
  }
  @Loggable
  def doComponentService(componentPackage: String, reuse: Boolean = true)(f: (ICtrlComponent) => Any): Unit = for {
    context <- AppActivity.Context
    inner <- AppActivity.Inner
  } {
    if (inner.bindedICtrlPool.isDefinedAt(componentPackage)) {
      log.debug("reuse service connection")
      f(inner.bindedICtrlPool(componentPackage)._2)
      return
    }
    // lock for bindService
    val lock = new Lock
    // service itself
    var service: ICtrlComponent = null
    // service connection
    val connection = new ComponentServiceConnection((_service) => {
      service = _service
      lock.release
    })
    val intent = new Intent(DIntent.ComponentService)
    intent.setPackage(componentPackage)
    lock.available = false
    if (context.bindService(intent, connection, Context.BIND_AUTO_CREATE)) {
      lock.acquire
      try {
        if (service != null) {
          if (reuse) {
            log.debug("add service connection to bindedICtrlPool")
            inner.bindedICtrlPool(componentPackage) = (connection, service)
          }
          f(service)
        }
      } finally {
        service = null
        if (!reuse)
          context.unbindService(connection)
      }
    } else {
      log.fatal("service bind failed")
    }
  }
  def serializeToList(o: java.io.Serializable): java.util.List[Byte] =
    serializeToArray(o).toList
  def serializeToArray(o: java.io.Serializable): Array[Byte] = {
    val baos = new ByteArrayOutputStream()
    val oos = new ObjectOutputStream(baos)
    oos.writeObject(o)
    oos.close()
    baos.toByteArray()
  }
  def deserializeFromList(s: java.util.List[Byte]): Option[Object] =
    if (s == null) None else deserializeFromArray(s.toList.toArray)
  def deserializeFromArray(s: Array[Byte]): Option[Object] = try {
    if (s == null) return None
    val ois = new ObjectInputStream(new ByteArrayInputStream(s.toList.toArray))
    val o = ois.readObject()
    ois.close()
    Some(o)
  } catch {
    case e =>
      log.error("deserialization error: " + e.getMessage())
      None
  }
  /**
   * Write to a stream
   *
   * @param in
   * @param out
   */
  def writeToStream(in: InputStream, out: OutputStream) {
    val buffer = new Array[Byte](8192)
    @tailrec
    def next(exit: Boolean = false) {
      if (exit) {
        in.close()
        out.close()
        return
      }
      val read = in.read(buffer)
      if (read > 0)
        out.write(buffer, 0, read)
      next(read == -1)
    }
    next()
  }
  private class ComponentServiceConnection(f: (ICtrlComponent) => Any) extends ServiceConnection() {
    def onServiceConnected(className: ComponentName, iservice: IBinder) {
      log.debug("connected to " + className + " service")
      f(ICtrlComponent.Stub.asInterface(iservice))
    }
    def onServiceDisconnected(className: ComponentName) {
      log.debug("unexpectedly disconnected from " + className + " service")
    }
  }
}
