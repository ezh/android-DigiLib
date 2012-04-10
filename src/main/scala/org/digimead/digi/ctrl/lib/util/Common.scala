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
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.InputStream
import java.io.InputStreamReader
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.OutputStream
import java.net.NetworkInterface
import java.nio.channels.FileChannel
import java.text.SimpleDateFormat
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.Date

import scala.Array.canBuildFrom
import scala.Option.option2Iterable
import scala.annotation.implicitNotFound
import scala.annotation.tailrec
import scala.collection.JavaConversions._
import scala.collection.immutable.HashMap
import scala.concurrent.Lock

import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.base.AppActivity
import org.digimead.digi.ctrl.lib.declaration.DConstant
import org.digimead.digi.ctrl.lib.declaration.DIntent
import org.digimead.digi.ctrl.lib.declaration.DTimeout
import org.digimead.digi.ctrl.lib.dialog.FailedMarket
import org.digimead.digi.ctrl.lib.dialog.InstallControl
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.Activity
import org.digimead.digi.ctrl.ICtrlComponent

import android.content.pm.PackageManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Environment
import android.os.IBinder
import android.text.ClipboardManager
import android.widget.Toast

object Common extends Logging {
  private lazy val df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZ")
  log.debug("alive")
  def dateString(date: Date) = df.format(date)
  @Loggable
  def onCreateDialog(id: Int, activity: Activity) = id match {
    case id if id == InstallControl.getId(activity) =>
      InstallControl.createDialog(activity)
    case id if id == FailedMarket.getId(activity) =>
      FailedMarket.createDialog(activity)
    case _ =>
      null
  }
  @Loggable
  def getDirectory(context: Context, name: String, forceInternal: Boolean = true): Option[File] = {
    var result: Option[File] = None
    if (!forceInternal) {
      // try to use external storage
      try {
        result = Option(context.getExternalFilesDir(null)).flatMap(base =>
          if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
            Some(new File(base, name))
          } else
            None)
        result.foreach(dir => {
          if (!dir.exists)
            dir.mkdirs
        })
      } catch {
        case e =>
          log.debug(e.getMessage, e)
          result = None
      }
    }
    if (result == None) {
      // try to use internal storage
      try {
        result = Option(context.getFilesDir()).flatMap(base => Some(new File(base, name)))
        result.foreach(dir => {
          if (!dir.exists)
            dir.mkdirs
        })
      } catch {
        case e =>
          log.debug(e.getMessage, e)
          result = None
      }
    }
    result
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
  def checkInterfaceInUse(interface: String, aclMask: String): Boolean = try {
    def check(acl: String, str: String): Boolean =
      str.matches(acl.replaceAll("""\*""", ".+"))
    val Array(acl0: String, acl1: String, acl2: String, acl3: String, acl4: String) = aclMask.split("[:.]")
    val Array(i0: String, i1: String, i2: String, i3: String, i4: String) = interface.split("[:.]")
    check(acl0, i0) & check(acl1, i1) & check(acl2, i2) & check(acl3, i3) & check(acl4, i4)
  } catch {
    case e =>
      log.error(e.getMessage, e)
      false
  }
  @Loggable
  def listPreparedFiles(context: Context): Option[Seq[File]] = for {
    appNativePath <- AppActivity.Inner.appNativePath
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
  def findBusyBox(): Option[File] = {
    var busybox: File = null
    busybox = new File("/sbin/ext/busybox")
    if (busybox.exists)
      return Some(busybox)
    busybox = new File("/system/bin/busybox")
    if (busybox.exists)
      return Some(busybox)
    busybox = new File("/system/xbin/busybox")
    if (busybox.exists)
      return Some(busybox)
    busybox = new File("/bin/busybox")
    if (busybox.exists)
      return Some(busybox)
    busybox = new File("/sbin/busybox")
    if (busybox.exists)
      return Some(busybox)
    busybox = new File("/xbin/busybox")
    if (busybox.exists)
      return Some(busybox)
    None
  }
  @Loggable
  def execChmod(permission: String, file: File, recursive: Boolean = false): Boolean = {
    val busybox = findBusyBox
    if (busybox == None)
      return false
    val args = if (recursive)
      Array(busybox.get.getAbsolutePath, "chmod", permission, file.getAbsolutePath)
    else
      Array(busybox.get.getAbsolutePath, "chmod", "-R", permission, file.getAbsolutePath)
    val p = Runtime.getRuntime().exec(args)
    val err = new BufferedReader(new InputStreamReader(p.getErrorStream()))
    p.waitFor()
    val retcode = p.exitValue()
    if (retcode != 0) {
      var error = err.readLine()
      while (error != null) {
        log.fatal("chmod error: " + error)
        error = err.readLine()
      }
      false
    } else
      true
  }
  @Loggable
  def doComponentService(componentPackage: String, reuse: Boolean = true, operationTimeout: Long = DTimeout.normal)(f: (ICtrlComponent) => Any): Unit = AppActivity.Context foreach {
    context =>
      val connectionGuard = Executors.newSingleThreadScheduledExecutor()
      if (AppActivity.Inner.bindedICtrlPool.isDefinedAt(componentPackage)) {
        log.debug("reuse service connection")
        f(AppActivity.Inner.bindedICtrlPool(componentPackage)._3)
        return
      }
      // lock for bindService
      val lock = new Lock
      connectionGuard.schedule(new Runnable {
        def run = {
          log.warn("doComponentService to \"" + DIntent.ComponentService + "\" hang")
          lock.release
        }
      }, operationTimeout, TimeUnit.MILLISECONDS)
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
        connectionGuard.shutdownNow
        val executionGuard = Executors.newSingleThreadExecutor()
        val executionFuture = executionGuard.submit(new Runnable {
          def run = try {
            if (service != null) {
              if (reuse) {
                log.debug("add service connection to bindedICtrlPool")
                AppActivity.Inner.bindedICtrlPool(componentPackage) = (context, connection, service)
              }
              f(service)
            }
          } finally {
            service = null
            if (!reuse)
              context.unbindService(connection)
          }
        })
        try {
          // Execute the job with a time limit  
          executionFuture.get(operationTimeout, TimeUnit.MILLISECONDS)
        } catch {
          case e: TimeoutException =>
            // Operation timed out, so log it and attempt to cancel the thread
            log.warn("doComponentService timeout", e)
            executionFuture.cancel(true)
            service = null
            if (!reuse)
              context.unbindService(connection)
        } finally {
          executionGuard.shutdown
        }
      } else {
        log.fatal("service bind failed")
      }
  }
  @Loggable
  def copyFile(sourceFile: File, destFile: File) {
    if (!destFile.exists()) {
      destFile.createNewFile();
    }
    var source: FileChannel = null
    var destination: FileChannel = null
    try {
      source = new FileInputStream(sourceFile).getChannel()
      destination = new FileOutputStream(destFile).getChannel()
      destination.transferFrom(source, 0, source.size())
    } finally {
      if (source != null) {
        source.close()
      }
      if (destination != null) {
        destination.close()
      }
    }
  }
  @Loggable
  def isSignedWithDebugKey(context: Context, clazz: Class[_], debugKey: String): Boolean = try {
    val c = new ComponentName(context, clazz)
    val pinfo = context.getPackageManager.getPackageInfo(c.getPackageName(), PackageManager.GET_SIGNATURES)
    val sigs = pinfo.signatures
    sigs.foreach(s => log.debug(s.toCharsString))
    if (debugKey == sigs.head.toCharsString) {
      log.debug("package " + c.getPackageName() + " has been signed with the debug key")
      true
    } else {
      log.debug("package " + c.getPackageName() + "signed with a key other than the debug key")
      false
    }
  } catch {
    case e =>
      log.error(e.getMessage, e)
      false
  }
  def unparcelFromList[T <: android.os.Parcelable](s: java.util.List[Byte], loader: ClassLoader = null)(implicit m: scala.reflect.Manifest[T]): Option[T] =
    if (s == null) None else unparcelFromArray[T](s.toList.toArray, loader)
  def unparcelFromArray[T <: android.os.Parcelable](s: Array[Byte], loader: ClassLoader = null)(implicit m: scala.reflect.Manifest[T]): Option[T] = try {
    val p = android.os.Parcel.obtain()
    p.unmarshall(s, 0, s.length)
    p.setDataPosition(0)
    val c = if (loader == null) Class.forName(m.erasure.getName) else Class.forName(m.erasure.getName, true, loader)
    val f = c.getField("CREATOR")
    val creator = f.get(null).asInstanceOf[android.os.Parcelable.Creator[T]]
    val result = Option(creator.createFromParcel(p))
    p.recycle()
    result
  } catch {
    case e =>
      log.error("unparcel error", e)
      None
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
  def deserializeFromList[T <: java.io.Serializable](s: java.util.List[Byte])(implicit m: scala.reflect.Manifest[T]): Option[T] =
    if (s == null) None else deserializeFromArray[T](s.toList.toArray)
  def deserializeFromArray[T <: java.io.Serializable](s: Array[Byte])(implicit m: scala.reflect.Manifest[T]): Option[T] = try {
    if (s == null) return None
    val ois = new ObjectInputStream(new ByteArrayInputStream(s.toList.toArray))
    val o = ois.readObject()
    ois.close()
    Some(o.asInstanceOf[T])
  } catch {
    case e =>
      log.error("deserialization error", e)
      None
  }
  @Loggable
  def writeToFile(file: File, text: String) {
    val fw = new FileWriter(file)
    try { fw.write(text) }
    finally { fw.close }
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
