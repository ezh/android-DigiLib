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
    val ComponentTS = Value("component_timestamp")
    val ComponentName = Value("component_name")
    val ComponentPackage = Value("component_package")
    val UserTS = Value("user_timestamp")
    val UserOrigin = Value("user_origin") // component id
    val UserName = Value("user_name")
    val ActivityTS = Value("activity_timestamp")
    val ActivityOrigin = Value("activity_origin") // component id
    val ActivitySeverity = Value("activity_severity")
    val ActivityMessage = Value("activity_message")
    val SessionTS = Value("session_timestamp")
    val SessionOrigin = Value("session_origin") // component id
    val SessionIP = Value("session_ip")
    val SessionDuration = Value("session_duration")
    val AuthTS = Value("auth_timestamp")
    val AuthOrigin = Value("auth_origin") // session id
    val AuthUser = Value("auth_user") // user id
    val AuthCode = Value("auth_code") // Session.Auth.Value: no, unknown user, failed, successful
  }
  object Uri extends Enumeration {
    val History = Value("content://" + authority + "/history")
    val HistoryID = Value("content://" + authority + "/history/#")
    val Activity = Value("content://" + authority + "/activity")
    val ActivityID = Value("content://" + authority + "/activity/#")
    val Session = Value("content://" + authority + "/session")
    val SessionID = Value("content://" + authority + "/session/#")
    val Auth = Value("content://" + authority + "/auth")
    val AuthID = Value("content://" + authority + "/auth/#")
  }
}
