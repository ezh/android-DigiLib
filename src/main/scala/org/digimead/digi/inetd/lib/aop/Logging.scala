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

package org.digimead.digi.inetd.lib.aop

import org.aspectj.lang.JoinPoint
import org.slf4j.LoggerFactory
import java.io.File

abstract class Logging {
  private final val log = LoggerFactory.getLogger("o.d.d.i.a.Logging")
  private final val pid = try {
    Integer.parseInt(new File("/proc/self").getCanonicalFile().getName())
  } catch {
    case e =>
      log.warn("get PID failed")
      -1
  }

  def enteringMethod(joinPoint: JoinPoint) {
    val tid = Thread.currentThread().getId()
    val source = joinPoint.getSourceLocation()
    val signature = joinPoint.getSignature()
    val className = signature.getDeclaringType().getSimpleName()
    val methodName = signature.getName()
    log.trace("[P" + pid + ":T%010d".format(tid) + "] enteringMethod " + className + "::" + methodName + " " + source.getFileName() + ":" + source.getLine())
  }
  def leavingMethod(joinPoint: JoinPoint) {
    val tid = Thread.currentThread().getId()
    val signature = joinPoint.getSignature()
    val className = signature.getDeclaringType().getSimpleName()
    val methodName = signature.getName()
    log.trace("[P" + pid + ":T%010d".format(tid) + "] leavingMethod " + className + "::" + methodName)
  }
  def leavingMethod(joinPoint: JoinPoint, returnValue: Object) {
    val tid = Thread.currentThread().getId()
    val signature = joinPoint.getSignature()
    val className = signature.getDeclaringType().getSimpleName()
    val methodName = signature.getName()
    log.trace("[P" + pid + ":T%010d".format(tid) + "] leavingMethod " + className + "::" + methodName + " result [" + returnValue + "]")
  }
  def leavingMethodException(joinPoint: JoinPoint, throwable: Exception) {
    val tid = Thread.currentThread().getId()
    val signature = joinPoint.getSignature();
    val className = signature.getDeclaringType().getSimpleName();
    val methodName = signature.getName();
    val exceptionMessage = throwable.getMessage();
    log.trace("[P" + pid + ":T%010d".format(tid) + "] leavingMethodException " + className + "::" + methodName + ". Reason: " + exceptionMessage)
  }
  protected def TRACE(msg: String) = log.trace(msg)
}

object Logging {
  final var enabled = true
}

/*

Example:

import org.digimead.digi.inetd.lib.aop.Loggable;

privileged public final aspect AspectLogging extends
		org.digimead.digi.inetd.lib.aop.Logging {
	public pointcut loggingNonVoid() : execution(@Loggable !void *(..));

	public pointcut loggingVoid() : execution(@Loggable void *(..));

	public pointcut logging() : loggingVoid() || loggingNonVoid();

	before() : logging() {
		if (org.digimead.digi.inetd.lib.aop.Logging.enabled())
			enteringMethod(thisJoinPoint);
	}

	after() returning(Object result) : loggingNonVoid() {
		if (org.digimead.digi.inetd.lib.aop.Logging.enabled())
			leavingMethod(thisJoinPoint, result);
	}

	after() returning() : loggingVoid() {
		if (org.digimead.digi.inetd.lib.aop.Logging.enabled())
			leavingMethod(thisJoinPoint);
	}

	after() throwing(Exception ex) : logging() {
		if (org.digimead.digi.inetd.lib.aop.Logging.enabled())
			leavingMethodException(thisJoinPoint, ex);
	}
}

*/
