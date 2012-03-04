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

/*
 * best viewer is *nix console with command line
 * grcat - generic colouriser grcat by Radovan Garabík
 * adb logcat -v threadtime | awk '{for(i=1;i<=NF;i++)if(i!=1&&i!=2)printf$i OFS;print""}' | grcat adb.conf
 * 
 * OR
 * 
 * adb logcat -v threadtime | awk '{
 *  for (i=1; i<=NF; i++) {
 *#    if (i==1) printf$i OFS;printf ""
 *#    if (i==2) printf$i OFS;printf ""
 *    if (i==3) printf(" P%05d",$i);
 *    if (i==4) printf(" T%05d",$i);
 *    if (i==5) printf(" %1s",$i);
 *    if (i==6) printf(" %-24s",$i);
 *    if (i>6) printf$i OFS;printf"";
 *  }
 *  print ""
 *}' | grcat adb.conf
 *
 *
 * example adb.conf
 *regexp=.*exceeds maximum length of 23 characters,.*
 *skip=yes
 *count=more
 *-
 *regexp=^ P\d{5} T\d{5} . @.*$
 *colours=bold
 *count=more
 *-
 *regexp=^ P\d{5} T\d{5} V @.*$
 *colours=bold black
 *count=more
 *
 * we may highlight with colors everything
 * anything like PID/TID/Class/File/Line/Level
 * and than filter or search
 */

package org.digimead.digi.ctrl.lib.aop

import java.io.File
import java.io.FileWriter
import java.io.FilenameFilter
import java.text.SimpleDateFormat
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.Date

import scala.Option.option2Iterable
import scala.concurrent.SyncVar

import org.aspectj.lang.Signature
import org.digimead.digi.ctrl.lib.base.AppActivity
import org.digimead.digi.ctrl.lib.declaration.DPreference
import org.digimead.digi.ctrl.lib.storage.GoogleCloud
import org.slf4j.LoggerFactory

import android.app.Activity
import android.content.Context

trait Logging {
  implicit protected val log: RichLogger = Logging.getLogger(this)
}

object Logging {
  var logPrefix = "@" // prefix for all adb logcat TAGs, everyone may change (but should not) it on his/her own risk
  @volatile
  var enabled = true
  def enteringMethod(file: String, line: Int, signature: Signature, obj: Logging) {
    val className = signature.getDeclaringType().getSimpleName()
    val methodName = signature.getName()
    obj.log.trace("[L%04d".format(line) + "] enteringMethod " + className + "::" + methodName)
  }
  def leavingMethod(file: String, line: Int, signature: Signature, obj: Logging) {
    val className = signature.getDeclaringType().getSimpleName()
    val methodName = signature.getName()
    obj.log.trace("[L%04d".format(line) + "] leavingMethod " + className + "::" + methodName)
  }
  def leavingMethod(file: String, line: Int, signature: Signature, obj: Logging, returnValue: Object) {
    val className = signature.getDeclaringType().getSimpleName()
    val methodName = signature.getName()
    obj.log.trace("[L%04d".format(line) + "] leavingMethod " + className + "::" + methodName + " result [" + returnValue + "]")
  }
  def leavingMethodException(file: String, line: Int, signature: Signature, obj: Logging, throwable: Exception) {
    val className = signature.getDeclaringType().getSimpleName()
    val methodName = signature.getName()
    val exceptionMessage = throwable.getMessage();
    obj.log.trace("[L%04d".format(line) + "] leavingMethodException " + className + "::" + methodName + ". Reason: " + exceptionMessage)
  }
  def getLogger(obj: Logging): RichLogger = {
    val stackArray = Thread.currentThread.getStackTrace().dropWhile(_.getClassName != getClass.getName)
    val stack = if (stackArray(1).getFileName != stackArray(0).getFileName)
      stackArray(1) else stackArray(2)
    val fileRaw = stack.getFileName.split("""\.""")
    val fileParsed = if (fileRaw.length > 1)
      fileRaw.dropRight(1).mkString(".")
    else
      fileRaw.head
    if (obj.getClass().toString.last == '$') // add object mart to file name
      new RichLogger(LoggerFactory.getLogger(logPrefix +
        obj.getClass.getPackage.getName.split("""\.""").last + "." + fileParsed + "$"))
    else
      new RichLogger(LoggerFactory.getLogger(logPrefix +
        obj.getClass.getPackage.getName.split("""\.""").last + "." + fileParsed))
  }
  def init(context: Context) =
    Report.init(context)
  object Report extends Logging {
    private[aop] val queue = new ConcurrentLinkedQueue[Record]
    private val run = new AtomicBoolean(true)
    val reportSuffix = "-" + System.currentTimeMillis + "-" + android.os.Process.myUid + "-" + android.os.Process.myPid + ".report"
    val reportName = "log" + reportSuffix
    private val reportThread = new Thread("GenericLogger " + reportName) {
      this.setDaemon(true)
      override def run() = info.get.foreach {
        info =>
          log.info("start report writing to " + reportName)
          val f = new File(info.reportPath, reportName)
          if (!f.getParentFile.exists)
            f.getParentFile().mkdirs()
          logWriter = new FileWriter(f, true)
          while (Report.this.run.get) {
            if (queue.isEmpty())
              Thread.sleep(500)
            else
              flush(1000)
          }
          logWriter.flush()
          logWriter.close()
          logWriter = null
      }
    }
    private val nullThread = new Thread("NullLogger " + reportName) {
      this.setDaemon(true)
      override def run() {
        while (Report.this.run.get) {
          if (queue.isEmpty())
            Thread.sleep(500)
          else
            queue.clear
        }
      }
    }
    private lazy val df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZ")
    val info = new SyncVar[Option[Info]]
    private var workerThread: Thread = null
    private var logWriter: FileWriter = null
    info.set(None)
    private[Logging] def init(context: Context): Unit = try {
      if (Report.info.get != None)
        return
      // Get information about the Package
      val pm = context.getPackageManager()
      val pi = pm.getPackageInfo(context.getPackageName(), 0)
      val pref = context.getSharedPreferences(DPreference.Log, Context.MODE_PRIVATE)
      val writeReport = pref.getBoolean(pi.packageName, true)
      val info = new Info(reportPath = new File(context.getFilesDir(), "report"),
        appVersion = pi.versionName,
        appPackage = pi.packageName,
        phoneModel = android.os.Build.MODEL,
        androidVersion = android.os.Build.VERSION.RELEASE,
        url = "",
        write = writeReport)
      Report.info.set(Some(info))
      // Try to create the files folder if it doesn't exist
      if (!info.reportPath.exists)
        info.reportPath.mkdir()
      // Try to submit reports if there any stack traces
      submit(context, true)
      if (info.write) {
        Runtime.getRuntime().addShutdownHook(new Thread() { override def run() = logWriter.flush })
        write()
      } else {
        drop()
      }
      workerThread.start()
    } catch {
      case e => log.error(e.getMessage, e)
    }
    private def submit(context: Context, force: Boolean): Unit = for {
      info <- Logging.Report.info.get
      context <- AppActivity.Context
    } {
      val filter = new FilenameFilter() {
        def accept(dir: File, name: String) =
          name.endsWith(".report")
      }
      log.debug("looking for error reports in: " + info.reportPath)
      val dir = new File(info.reportPath + "/")
      val reports = Option(dir.list(filter)).flatten
      if (reports.isEmpty)
        return
      context match {
        case activity: Activity =>
          try {
            if (workerThread != null) {
              Report.this.run.set(false)
              workerThread.join()
            }
            if (force || reports.exists(_.startsWith("stacktrace"))) {
              reports.foreach(name => {
                val report = new File(info.reportPath, name)
                GoogleCloud.upload(report)
              })
            }
          } catch {
            case e =>
              log.error(e.getMessage, e)
          } finally {
            try {
              reports.foreach(name => {
                val report = new File(info.reportPath, name)
                log.info("delete " + report)
                report.delete
              })
              if (workerThread != null) {
                Report.this.run.set(true)
                workerThread.start()
              }
            } catch {
              case e =>
                log.error(e.getMessage, e)
            }
          }
        case context =>
          log.info("unable to send application report from unknown context")
      }
    }
    private def write() {
      workerThread = reportThread
    }
    private def drop() {
      workerThread = nullThread
    }
    private def close() = logWriter.close()
    private def flush(): Int = flush(java.lang.Integer.MAX_VALUE)
    private def flush(n: Int): Int = synchronized {
      var count = 0
      while (count < n && !queue.isEmpty()) {
        val record = queue.poll()
        logWriter.write(record + "\n")
        count += 1;
      }
      if (count > 0)
        logWriter.flush()
      count
    }
    private def dateString(date: Date) = df.format(date)
    private[aop] class Record(val date: Date,
      val pid: Int,
      val tid: Long,
      val level: Char,
      val tag: String,
      val message: String) {
      override def toString =
        Seq(Report.dateString(date), pid.toString, tid.toString, level.toString, tag.toString).mkString(" ") + ": " + message
    }
    case class Info(val reportPath: File,
      val appVersion: String,
      val appPackage: String,
      val phoneModel: String,
      val androidVersion: String,
      val url: String,
      val write: Boolean) {
      log.debug("reportPath: " + reportPath)
      log.debug("appVersion: " + appVersion)
      log.debug("appPackage: " + appPackage)
      log.debug("phoneModel: " + phoneModel)
      log.debug("androidVersion: " + androidVersion)
      log.debug("url: " + url)
      log.debug("write to storage: " + write)
    }
  }
}

/*

Example:

import org.aspectj.lang.reflect.SourceLocation;

privileged public final aspect AspectLogging {
	public pointcut loggingNonVoid(Logging obj, Loggable loggable) : target(obj) && execution(@Loggable !void *(..)) && @annotation(loggable);

	public pointcut loggingVoid(Logging obj, Loggable loggable) : target(obj) && execution(@Loggable void *(..)) && @annotation(loggable);

	public pointcut logging(Logging obj, Loggable loggable) : loggingVoid(obj, loggable) || loggingNonVoid(obj, loggable);

	before(final Logging obj, final Loggable loggable) : logging(obj, loggable) {
		SourceLocation location = thisJoinPointStaticPart.getSourceLocation();
		if (org.digimead.digi.ctrl.lib.aop.Logging$.MODULE$.enabled())
			org.digimead.digi.ctrl.lib.aop.Logging$.MODULE$.enteringMethod(
					location.getFileName(), location.getLine(),	thisJoinPointStaticPart.getSignature(), obj);
	}

	after(final Logging obj, final Loggable loggable) returning(final Object result) : loggingNonVoid(obj, loggable) {
		SourceLocation location = thisJoinPointStaticPart.getSourceLocation();
		if (org.digimead.digi.ctrl.lib.aop.Logging$.MODULE$.enabled())
			if (loggable.result())
				org.digimead.digi.ctrl.lib.aop.Logging$.MODULE$.leavingMethod(
						location.getFileName(), location.getLine(),	thisJoinPointStaticPart.getSignature(), obj, result);
			else
				org.digimead.digi.ctrl.lib.aop.Logging$.MODULE$
						.leavingMethod(location.getFileName(), location.getLine(),	thisJoinPointStaticPart.getSignature(), obj);
	}

	after(final Logging obj, final Loggable loggable) returning() : loggingVoid(obj, loggable) {
		SourceLocation location = thisJoinPointStaticPart.getSourceLocation();
		if (org.digimead.digi.ctrl.lib.aop.Logging$.MODULE$.enabled())
			org.digimead.digi.ctrl.lib.aop.Logging$.MODULE$
					.leavingMethod(location.getFileName(), location.getLine(),	thisJoinPointStaticPart.getSignature(), obj);
	}

	after(final Logging obj, final Loggable loggable) throwing(final Exception ex) : logging(obj, loggable) {
		SourceLocation location = thisJoinPointStaticPart.getSourceLocation();
		if (org.digimead.digi.ctrl.lib.aop.Logging$.MODULE$.enabled())
			org.digimead.digi.ctrl.lib.aop.Logging$.MODULE$
					.leavingMethodException(location.getFileName(), location.getLine(),	thisJoinPointStaticPart.getSignature(), obj, ex);
	}
}

*/
