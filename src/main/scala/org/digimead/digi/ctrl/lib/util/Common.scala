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

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.InputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.NetworkInterface
import java.nio.channels.FileChannel
import java.text.SimpleDateFormat
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.Date
import scala.annotation.tailrec
import scala.collection.JavaConversions._
import scala.collection.immutable.HashMap
import scala.concurrent.Lock
import scala.util.control.ControlThrowable
import org.digimead.digi.ctrl.lib.aop.Loggable
import org.digimead.digi.ctrl.lib.base.AppComponent
import org.digimead.digi.ctrl.lib.declaration.DConnection
import org.digimead.digi.ctrl.lib.declaration.DIntent
import org.digimead.digi.ctrl.lib.declaration.DTimeout
import org.digimead.digi.ctrl.lib.dialog.FailedMarket
import org.digimead.digi.ctrl.lib.dialog.InstallControl
import org.digimead.digi.ctrl.lib.log.Logging
import org.digimead.digi.ctrl.lib.log.RichLogger
import org.digimead.digi.ctrl.lib.message.Dispatcher
import org.digimead.digi.ctrl.lib.DActivity
import org.digimead.digi.ctrl.ICtrlComponent
import android.app.Activity
import android.app.Dialog
import android.content.pm.PackageManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Environment
import android.os.IBinder
import java.io.BufferedWriter

object Common extends Logging {
  private lazy val df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZ")
  @volatile private[util] var externalStorageDisabled: Option[Boolean] = None
  log.debug("alive")
  def dateString(date: Date) = df.format(date)
  def dateFile(date: Date) = dateString(date).replaceAll("""[:\.]""", "_").replaceAll("""\+""", "x")
  @Loggable
  def onCreateDialog(id: Int, activity: Activity with DActivity)(implicit logger: RichLogger, dispatcher: Dispatcher): Dialog = id match {
    case id if id == InstallControl.getId(activity) =>
      InstallControl.createDialog(activity)(logger, dispatcher)
    case id if id == FailedMarket.getId(activity) =>
      FailedMarket.createDialog(activity)
    case _ =>
      null
  }
  // -rwx--x--x 711
  @Loggable
  def getDirectory(context: Context, name: String, forceInternal: Boolean,
    allRead: Option[Boolean], allWrite: Option[Boolean], allExecute: Option[Boolean]): Option[File] = {
    var directory: Option[File] = None
    var isExternal = true
    var isNew = false
    log.debug("get working directory, mode 'force internal': " + forceInternal)
    if (!forceInternal && externalStorageDisabled != Some(true)) {
      // try to use external storage
      try {
        directory = Option(Environment.getExternalStorageDirectory).flatMap(preBase => {
          val isMounted = Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
          val baseAndroid = new File(preBase, "Android")
          val baseAndroidData = new File(baseAndroid, "data")
          val basePackage = new File(baseAndroidData, context.getPackageName)
          val baseFiles = new File(basePackage, "files")
          log.debug("try SD storage directory " + basePackage + ", SD storage is mounted: " + isMounted)
          if (isMounted) {
            var baseReady = true
            if (baseReady && !baseAndroid.exists) {
              if (!baseAndroid.mkdir) {
                log.error("mkdir '" + baseAndroid + "' failed")
                baseReady = false
              }
            }
            if (baseReady && !baseAndroidData.exists) {
              if (!baseAndroidData.mkdir) {
                log.error("mkdir '" + baseAndroidData + "' failed")
                baseReady = false
              }
            }
            if (baseReady && !basePackage.exists) {
              if (!basePackage.mkdir) {
                log.error("mkdir '" + basePackage + "' failed")
                baseReady = false
              }
            }
            if (baseReady && !baseFiles.exists) {
              if (!baseFiles.mkdir) {
                log.error("mkdir '" + baseFiles + "' failed")
                baseReady = false
              }
            }
            if (externalStorageDisabled == None) {
              try {
                log.debug("test external storage")
                val testFile = new File(baseFiles, "testExternalStorage.tmp")
                val testContent = (for (i <- 0 until 1024) yield i).mkString // about 2.9 kB
                val out = new BufferedWriter(new FileWriter(testFile))
                out.write(testContent)
                out.close()
                assert(testFile.length == testContent.length)
                if (scala.io.Source.fromFile(testFile).getLines.mkString == testContent) {
                  log.debug("external storge test successful")
                  externalStorageDisabled = Some(false)
                } else {
                  log.debug("external storge test failed")
                  externalStorageDisabled = Some(true)
                }
              } catch {
                case e =>
                  log.debug("external storge test failed, " + e.getMessage)
                  externalStorageDisabled = Some(true)
              }
            }
            if (baseReady)
              Some(new File(baseFiles, name))
            else
              None
          } else
            None
        })
        if (directory == None)
          log.warn("external storage " + Option(Environment.getExternalStorageDirectory) + " unavailable")
        directory.foreach(dir => {
          if (!dir.exists) {
            log.warn("directory " + dir + " does not exists, creating")
            if (dir.mkdir)
              isNew = true
            else {
              log.error("mkdir '" + dir + "' failed")
              directory = None
            }
          }
        })
      } catch {
        case e =>
          log.debug(e.getMessage, e)
          directory = None
      }
    }
    if (directory == None) {
      // try to use internal storage
      isExternal = false
      try {
        directory = Option(context.getFilesDir()).flatMap(base => Some(new File(base, name)))
        directory.foreach(dir => {
          if (!dir.exists)
            if (dir.mkdir)
              isNew = true
            else {
              log.error("mkdir '" + dir + "' failed")
              directory = None
            }
        })
      } catch {
        case e =>
          log.debug(e.getMessage, e)
          directory = None
      }
    }
    if (directory != None && isNew && !isExternal) {
      allRead match {
        case Some(true) => directory.get.setReadable(true, false)
        case Some(false) => directory.get.setReadable(true, true)
        case None => directory.get.setReadable(false, false)
      }
      allWrite match {
        case Some(true) => directory.get.setWritable(true, false)
        case Some(false) => directory.get.setWritable(true, true)
        case None => directory.get.setWritable(false, false)
      }
      allExecute match {
        case Some(true) => directory.get.setExecutable(true, false)
        case Some(false) => directory.get.setExecutable(true, true)
        case None => directory.get.setExecutable(false, false)
      }
    }
    directory
  }
  @Loggable(result = false)
  def getPublicPreferences(context: Context): PublicPreferences =
    PublicPreferences(context)
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
    log.debug("check interface " + interface + " against " + aclMask)
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
  def existsInConnectionFilter(service: ICtrlComponent, connection: DConnection, isAllowACL: Boolean): Boolean = try {
    AppComponent.Context.map {
      context =>
        val ip = try {
          InetAddress.getByAddress(BigInt(connection.remoteIP).toByteArray).getHostAddress
        } catch {
          case e =>
            log.warn(e.getMessage)
            return false
        }
        log.debug("check connection with remote IP " + ip + " against " + (if (isAllowACL) "allow ACL" else "deny ACL"))
        val acl = ip.split("""\.""") match {
          case Array(ip1, ip2, ip3, ip4) =>
            (ip1 + """\."""" + ip2 + """\."""" + ip3 + """\."""" + ip4).replaceAll("""\*""", ".+")
          case _ =>
            null
        }
        (if (isAllowACL) service.accessAllowRules else service.accessDenyRules).foreach {
          acl =>
            if (ip == acl)
              return true
            else {
              if (acl != null && ip.matches(acl))
                return true
            }
        }
        false
    } getOrElse false
  } catch {
    case ce: ControlThrowable => throw ce // propagate
    case e =>
      log.error(e.getMessage, e)
      false
  }
  @Loggable
  def doComponentService(componentPackage: String, reuse: Boolean = true, operationTimeout: Long = DTimeout.long)(f: (ICtrlComponent) => Any): Unit = AppComponent.Context foreach {
    context =>
      val bindContext = context.getApplicationContext()
      log.debug("start doComponentService for " + componentPackage + " with timeout " + operationTimeout)
      val connectionGuard = Executors.newSingleThreadScheduledExecutor()
      if (AppComponent.Inner.bindedICtrlPool.isDefinedAt(componentPackage)) {
        log.debug("reuse service connection")
        val (context, connection, service) = AppComponent.Inner.bindedICtrlPool(componentPackage)
        if (service.asBinder.isBinderAlive && service.asBinder.pingBinder) {
          log.debug("binder alive")
          f(service)
          return
        } else {
          log.warn("try to unbind dead service")
          context.unbindService(connection)
          AppComponent.Inner.bindedICtrlPool.remove(componentPackage)
        }
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
      if (bindContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)) {
        lock.acquire
        connectionGuard.shutdownNow
        val executionGuard = Executors.newSingleThreadExecutor()
        val executionFuture = executionGuard.submit(new Runnable {
          def run = try {
            if (service != null) {
              if (reuse) {
                log.debug("add service connection to bindedICtrlPool")
                AppComponent.Inner.bindedICtrlPool(componentPackage) = (bindContext, connection, service)
              }
              f(service)
            }
          } finally {
            service = null
            if (!reuse)
              bindContext.unbindService(connection)
          }
        })
        try {
          // Execute the job with a time limit
          executionFuture.get(operationTimeout, TimeUnit.MILLISECONDS)
        } catch {
          case ce: ControlThrowable => throw ce // propagate
          case e: TimeoutException =>
            // Operation timed out, so log it and attempt to cancel the thread
            log.warn("doComponentService " + componentPackage + " timeout", e)
            executionFuture.cancel(true)
            service = null
            if (!reuse)
              bindContext.unbindService(connection)
        } finally {
          executionGuard.shutdown
        }
      } else {
        log.fatal("context.bindService failed with context " + bindContext + " and intent " + intent)
      }
  }
  @Loggable
  def removeCachedComponentService(componentPackage: String) = try {
    if (AppComponent.Inner.bindedICtrlPool.isDefinedAt(componentPackage)) {
      log.debug("reuse service connection")
      val (context, connection, service) = AppComponent.Inner.bindedICtrlPool(componentPackage)
      log.warn("try to unbind cached service")
      context.unbindService(connection)
      AppComponent.Inner.bindedICtrlPool.remove(componentPackage)
    }
  } catch {
    case e =>
      log.error(e.getMessage, e)
  }
  @Loggable
  def copyFile(sourceFile: File, destFile: File): Boolean = {
    if (!destFile.exists())
      destFile.createNewFile()
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
    sourceFile.length == destFile.length
  }
  @Loggable
  def deleteFile(dfile: File): Unit =
    if (dfile.isDirectory) deleteFileRecursive(dfile) else dfile.delete
  private def deleteFileRecursive(dfile: File) {
    if (dfile.isDirectory)
      dfile.listFiles.foreach { f => deleteFile(f) }
    dfile.delete
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
  def parcelToList(o: android.os.Parcelable, flags: Int = 0): java.util.List[Byte] =
    parcelToArray(o).toList
  def parcelToArray(o: android.os.Parcelable, flags: Int = 0): Array[Byte] = {
    val parcel = android.os.Parcel.obtain
    o.writeToParcel(parcel, flags)
    val result = parcel.marshall
    parcel.recycle()
    result
  }
  def unparcelFromList[T <: android.os.Parcelable](s: java.util.List[Byte], loader: ClassLoader = null)(implicit m: scala.reflect.Manifest[T]): Option[T] =
    if (s == null) None else unparcelFromArray[T](s.toList.toArray, loader)
  def unparcelFromArray[T <: android.os.Parcelable](s: Array[Byte], loader: ClassLoader = null)(implicit m: scala.reflect.Manifest[T]): Option[T] = try {
    if (s == null) return None
    assert(m.erasure.getName != "java.lang.Object")
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
      log.error("unparcel '" + m.erasure.getName + "' error", e)
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
