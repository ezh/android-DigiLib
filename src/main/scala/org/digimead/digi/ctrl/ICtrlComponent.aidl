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

interface ICtrlComponent {
  // serialized to Array[Byte] Common.ComponentInfo
  List info();
  int uid();
  int size();
  boolean pre(in int id, in String workdir);
  // serialized to Array[Byte] Common.ExecutableEnvironment
  List executable(in int id, in String workdir);
  boolean post(in int id, in String workdir);
}