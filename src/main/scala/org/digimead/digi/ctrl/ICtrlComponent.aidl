/*
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

package org.digimead.digi.ctrl;

import org.digimead.digi.ctrl.lib.info.ExecutableInfo;
import org.digimead.digi.ctrl.lib.info.ComponentInfo;
import org.digimead.digi.ctrl.lib.info.UserInfo;

interface ICtrlComponent {
  // serialized to Array[Byte] Common.ComponentInfo
  ComponentInfo info();
  int uid();
  int size();
  boolean pre(in int id, in String workdir);
  ExecutableInfo executable(in int id, in String workdir);
  boolean post(in int id, in String workdir);
  List<String> accessAllowRules();
  List<String> accessDenyRules();
  boolean readBooleanProperty(in int property);
  int readIntProperty(in int property);
  String readStringProperty(in int property);
  boolean accessRulesOrder(); // ADA vs DAD
  Map interfaceRules(); // String, Boolean
  UserInfo user(in String name);
}
