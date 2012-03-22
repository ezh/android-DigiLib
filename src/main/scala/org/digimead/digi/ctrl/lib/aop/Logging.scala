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

import org.aspectj.lang.Signature
import org.digimead.digi.ctrl.lib.log.{ Logging => LLogging }

object Logging {
  def enteringMethod(file: String, line: Int, signature: Signature, obj: AnyRef) {
    obj match {
      case logging: LLogging =>
        if (!logging.log.isTraceEnabled) return
        val className = signature.getDeclaringType().getSimpleName()
        val methodName = signature.getName()
        logging.log.trace("[L%04d".format(line) + "] enteringMethod " + className + "::" + methodName)
      case other =>
        if (!LLogging.commonLogger.isTraceEnabled) return
        val className = signature.getDeclaringType().getSimpleName()
        val methodName = signature.getName()
        LLogging.commonLogger.trace("[L%04d".format(line) + "] enteringMethod " + className + "::" + methodName + " at " + file.takeWhile(_ != '.'))
    }
  }
  def leavingMethod(file: String, line: Int, signature: Signature, obj: AnyRef) {
    obj match {
      case logging: LLogging =>
        if (!logging.log.isTraceEnabled) return
        val className = signature.getDeclaringType().getSimpleName()
        val methodName = signature.getName()
        logging.log.trace("[L%04d".format(line) + "] leavingMethod " + className + "::" + methodName)
      case other =>
        if (!LLogging.commonLogger.isTraceEnabled) return
        val className = signature.getDeclaringType().getSimpleName()
        val methodName = signature.getName()
        LLogging.commonLogger.trace("[L%04d".format(line) + "] leavingMethod " + className + "::" + methodName + " at " + file.takeWhile(_ != '.'))
    }
  }
  def leavingMethod(file: String, line: Int, signature: Signature, obj: AnyRef, returnValue: Object) {
    obj match {
      case logging: LLogging =>
        if (!logging.log.isTraceEnabled) return
        val className = signature.getDeclaringType().getSimpleName()
        val methodName = signature.getName()
        logging.log.trace("[L%04d".format(line) + "] leavingMethod " + className + "::" + methodName + " result [" + returnValue + "]")
      case other =>
        if (!LLogging.commonLogger.isTraceEnabled) return
        val className = signature.getDeclaringType().getSimpleName()
        val methodName = signature.getName()
        LLogging.commonLogger.trace("[L%04d".format(line) + "] leavingMethod " + className + "::" + methodName + " at " + file.takeWhile(_ != '.') + " result [" + returnValue + "]")
    }
  }
  def leavingMethodException(file: String, line: Int, signature: Signature, obj: AnyRef, throwable: Exception) {
    obj match {
      case logging: LLogging =>
        if (!logging.log.isTraceEnabled) return
        val className = signature.getDeclaringType().getSimpleName()
        val methodName = signature.getName()
        val exceptionMessage = throwable.getMessage();
        logging.log.trace("[L%04d".format(line) + "] leavingMethodException " + className + "::" + methodName + ". Reason: " + exceptionMessage)
      case other =>
        if (!LLogging.commonLogger.isTraceEnabled) return
        val className = signature.getDeclaringType().getSimpleName()
        val methodName = signature.getName()
        val exceptionMessage = throwable.getMessage();
        LLogging.commonLogger.trace("[L%04d".format(line) + "] leavingMethodException " + className + "::" + methodName + " at " + file.takeWhile(_ != '.') + ". Reason: " + exceptionMessage)
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
		if (org.digimead.digi.ctrl.lib.log.Logging$.MODULE$.enabled())
			org.digimead.digi.ctrl.lib.log.Logging$.MODULE$.enteringMethod(
					location.getFileName(), location.getLine(),	thisJoinPointStaticPart.getSignature(), obj);
	}

	after(final Logging obj, final Loggable loggable) returning(final Object result) : loggingNonVoid(obj, loggable) {
		SourceLocation location = thisJoinPointStaticPart.getSourceLocation();
		if (org.digimead.digi.ctrl.lib.log.Logging$.MODULE$.enabled())
			if (loggable.result())
				org.digimead.digi.ctrl.lib.log.Logging$.MODULE$.leavingMethod(
						location.getFileName(), location.getLine(),	thisJoinPointStaticPart.getSignature(), obj, result);
			else
				org.digimead.digi.ctrl.lib.log.Logging$.MODULE$
						.leavingMethod(location.getFileName(), location.getLine(),	thisJoinPointStaticPart.getSignature(), obj);
	}

	after(final Logging obj, final Loggable loggable) returning() : loggingVoid(obj, loggable) {
		SourceLocation location = thisJoinPointStaticPart.getSourceLocation();
		if (org.digimead.digi.ctrl.lib.log.Logging$.MODULE$.enabled())
			org.digimead.digi.ctrl.lib.log.Logging$.MODULE$
					.leavingMethod(location.getFileName(), location.getLine(),	thisJoinPointStaticPart.getSignature(), obj);
	}

	after(final Logging obj, final Loggable loggable) throwing(final Exception ex) : logging(obj, loggable) {
		SourceLocation location = thisJoinPointStaticPart.getSourceLocation();
		if (org.digimead.digi.ctrl.lib.log.Logging$.MODULE$.enabled())
			org.digimead.digi.ctrl.lib.log.Logging$.MODULE$
					.leavingMethodException(location.getFileName(), location.getLine(),	thisJoinPointStaticPart.getSignature(), obj, ex);
	}
}

*/
