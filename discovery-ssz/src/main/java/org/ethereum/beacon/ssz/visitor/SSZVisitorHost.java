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

import static org.ethereum.beacon.ssz.type.SSZType.Type.BASIC;
import static org.ethereum.beacon.ssz.type.SSZType.Type.CONTAINER;
import static org.ethereum.beacon.ssz.type.SSZType.Type.LIST;
import static org.ethereum.beacon.ssz.type.SSZType.Type.UNION;
import static org.ethereum.beacon.ssz.type.SSZType.Type.VECTOR;

import org.ethereum.beacon.ssz.type.SSZBasicType;
import org.ethereum.beacon.ssz.type.SSZContainerType;
import org.ethereum.beacon.ssz.type.SSZType;
import org.ethereum.beacon.ssz.type.SSZUnionType;
import org.ethereum.beacon.ssz.type.list.SSZListType;

public class SSZVisitorHost {

  public <ResultType, ParamType> ResultType handleAny(
      SSZType type, ParamType value, SSZVisitor<ResultType, ParamType> visitor) {

    if (type.getType() == BASIC) {
      return visitor.visitBasicValue((SSZBasicType) type, value);
    } else if (type.getType() == LIST || type.getType() == VECTOR) {
      SSZListType listType = (SSZListType) type;
      return visitor.visitList(
          listType, value, (idx, param) -> handleAny(listType.getElementType(), param, visitor));
    } else if (type.getType() == UNION) {
      SSZUnionType unionType = (SSZUnionType) type;
      return visitor.visitUnion(
          unionType,
          value,
          (idx, param) -> handleAny(unionType.getChildTypes().get(idx), param, visitor));
    } else if (type.getType() == CONTAINER) {
      SSZContainerType containerType = (SSZContainerType) type;
      return visitor.visitContainer(
          containerType,
          value,
          (idx, param) -> handleAny(containerType.getChildTypes().get(idx), param, visitor));
    } else {
      throw new IllegalArgumentException("Unknown type: " + type);
    }
  }

  public <ResultType, ParamType> ResultType handleSubList(
      SSZListType type,
      ParamType value,
      int startIdx,
      int len,
      SSZVisitor<ResultType, ParamType> visitor) {

    return visitor.visitSubList(
        type,
        value,
        startIdx,
        len,
        (idx, param) -> handleAny(type.getElementType(), param, visitor));
  }
}
