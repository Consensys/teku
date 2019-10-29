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

import static org.ethereum.beacon.ssz.type.SSZType.Type.CONTAINER;
import static org.javatuples.Pair.with;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import net.consensys.cava.bytes.Bytes;
import org.ethereum.beacon.ssz.access.SSZCompositeAccessor.CompositeInstanceBuilder;
import org.ethereum.beacon.ssz.annotation.SSZSerializable;
import org.ethereum.beacon.ssz.type.SSZBasicType;
import org.ethereum.beacon.ssz.type.SSZCompositeType;
import org.ethereum.beacon.ssz.type.SSZContainerType;
import org.ethereum.beacon.ssz.type.SSZType;
import org.ethereum.beacon.ssz.type.SSZUnionType;
import org.ethereum.beacon.ssz.type.list.SSZListType;
import org.javatuples.Pair;

/** SSZ deserializer with offset-based decoding of variable sized elements */
public class SosDeserializer implements SSZVisitor<Object, Bytes> {
  static final int BYTES_PER_LENGTH_OFFSET = 4;

  /**
   * Decodes basic value
   *
   * @param sszType Type of field, should be some basic type
   * @param bytes Bytes data
   * @return Decoded object
   */
  @Override
  public Object visitBasicValue(SSZBasicType sszType, Bytes bytes) {
    return sszType.getAccessor().decode(sszType.getTypeDescriptor(), new SSZReader(bytes));
  }

  @Override
  public Object visitUnion(
      SSZUnionType type, Bytes bytes, ChildVisitor<Bytes, Object> childVisitor) {
    int typeIndex = deserializeLength(bytes.slice(0, BYTES_PER_LENGTH_OFFSET));
    Bytes body = bytes.slice(BYTES_PER_LENGTH_OFFSET);

    CompositeInstanceBuilder instanceBuilder = type.getAccessor().createInstanceBuilder(type);
    if (typeIndex == 0 && type.isNullable()) {
      instanceBuilder.setChild(typeIndex, null);
    } else {
      Object decodeResult = childVisitor.apply(typeIndex, body);
      instanceBuilder.setChild(typeIndex, decodeResult);
    }
    return instanceBuilder.build();
  }

  @Override
  public Object visitContainer(
      SSZContainerType type, Bytes param, ChildVisitor<Bytes, Object> childVisitor) {
    SSZSerializable annotation =
        type.getTypeDescriptor().getRawClass().getAnnotation(SSZSerializable.class);
    if (annotation != null && annotation.skipContainer()) {
      if (type.getChildTypes().size() != 1) {
        throw new IllegalArgumentException(
            "Only container with a single child can be skipped: " + type);
      }
      CompositeInstanceBuilder instanceBuilder = type.getAccessor().createInstanceBuilder(type);
      Object child = childVisitor.apply(0, param);
      instanceBuilder.setChild(0, child);
      return instanceBuilder.build();
    } else {
      return visitComposite(type, param, childVisitor);
    }
  }

  /**
   * Decodes composite value
   *
   * @param type Type of field, should be some composite type
   * @param bytes Bytes data
   * @param childVisitor Visitor which will be used for children
   * @return Decoded object
   */
  @Override
  public Object visitComposite(
      SSZCompositeType type, Bytes bytes, ChildVisitor<Bytes, Object> childVisitor) {

    CompositeInstanceBuilder instanceBuilder = type.getAccessor().createInstanceBuilder(type);
    int fixedPartEnd = bytes.size();
    int curOff = 0;
    int childIndex = 0;
    List<Pair<Integer, Integer>> varSizeChildren = new ArrayList<>();
    while (curOff < fixedPartEnd) {
      int childSize = getChildSize(type, childIndex);
      if (childSize == SSZType.VARIABLE_SIZE) {
        int bodyOff = deserializeLength(bytes.slice(curOff, BYTES_PER_LENGTH_OFFSET));
        fixedPartEnd = fixedPartEnd > bodyOff ? bodyOff : fixedPartEnd;
        varSizeChildren.add(with(childIndex, bodyOff));
        curOff += BYTES_PER_LENGTH_OFFSET;
      } else {
        Object result = childVisitor.apply(childIndex, bytes.slice(curOff, childSize));
        curOff += childSize;
        instanceBuilder.setChild(childIndex, result);
      }
      childIndex++;
    }
    varSizeChildren.add(with(-1, bytes.size()));
    for (int i = 0; i < varSizeChildren.size() - 1; i++) {
      Pair<Integer, Integer> child = varSizeChildren.get(i);
      childIndex = child.getValue0();
      int childStart = child.getValue1();
      int childEnd = varSizeChildren.get(i + 1).getValue1();
      Object result =
          childVisitor.apply(childIndex, bytes.slice(childStart, childEnd - childStart));
      instanceBuilder.setChild(childIndex, result);
    }
    return instanceBuilder.build();
  }

  private int getChildSize(SSZCompositeType type, int index) {
    if (type.getType() == CONTAINER) {
      return ((SSZContainerType) type).getChildTypes().get(index).getSize();
    } else {
      return ((SSZListType) type).getElementType().getSize();
    }
  }

  private int deserializeLength(Bytes lenBytes) {
    return lenBytes.toInt(ByteOrder.LITTLE_ENDIAN);
  }
}
