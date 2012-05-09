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

package org.digimead.digi.ctrl.lib.declaration

object DHistoryProvider {
  implicit def value2uri(v: Enumeration#Value): android.net.Uri = android.net.Uri.parse(v.toString)
  val authority = DConstant.controlPackage + ".history"
  case class Row(val session_id: Int,
    val process_id: Int,
    val component: Array[Byte],
    val executable: Array[Byte],
    val connection: Array[Byte])
  object Field extends Enumeration {
    val ID = Value("_id")
    val ComponentTS = Value("timestamp")
    val ComponentName = Value("name")
    val ComponentPackage = Value("package")
    val UserTS = Value("timestamp")
    val UserOrigin = Value("origin")
    val UserName = Value("name")
    val ActivityTS = Value("timestamp")
    val ActivityOrigin = Value("origin")
    val ActivitySeverity = Value("severity")
    val ActivityMessage = Value("message")
    val SessionTS = Value("timestamp")
    val SessionOrigin = Value("origin")
    val SessionIP = Value("ip")
    val SessionUser = Value("user")
    val SessionDuration = Value("duration")
    val SessionState = Value("state") // 0 - auth failed, 1 - auth successful, exit normally, 2 - auth successful, exit with error
  }
  object Uri extends Enumeration {
    val History = Value("content://" + authority + "/history")
    val HistoryID = Value("content://" + authority + "/history/#")
    val Activity = Value("content://" + authority + "/activity")
    val ActivityID = Value("content://" + authority + "/activity/#")
    val Sessions = Value("content://" + authority + "/sessions")
    val SessionsID = Value("content://" + authority + "/sessions/#")
  }
}
