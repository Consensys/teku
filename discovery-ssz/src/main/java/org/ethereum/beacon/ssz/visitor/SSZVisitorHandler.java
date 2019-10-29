/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.ethereum.beacon.ssz.visitor;

import org.ethereum.beacon.ssz.type.SSZType;
import org.ethereum.beacon.ssz.type.list.SSZListType;

/** Abstract implementation of specific visitor pattern */
public interface SSZVisitorHandler<ResultType> {

  ResultType visitAny(SSZType descriptor, Object value);

  ResultType visitList(SSZListType descriptor, Object listValue, int startIdx, int len);
}
