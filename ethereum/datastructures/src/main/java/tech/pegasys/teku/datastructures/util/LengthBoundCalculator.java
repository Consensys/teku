/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.datastructures.util;

import static tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer.BOOLEAN_SIZE;
import static tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer.getOptionalReflectionInfo;
import static tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer.getRequiredReflectionInfo;
import static tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer.isBitvector;
import static tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer.isPrimitive;
import static tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer.isVariable;
import static tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer.isVector;
import static tech.pegasys.teku.util.config.Constants.BYTES_PER_LENGTH_OFFSET;

import java.lang.reflect.Field;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.Bitlist;
import tech.pegasys.teku.ssz.SSZTypes.Bitvector;
import tech.pegasys.teku.ssz.SSZTypes.Bytes4;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;
import tech.pegasys.teku.ssz.sos.ReflectionInformation;

public class LengthBoundCalculator {

  static <T> LengthBounds calculateLengthBounds(final Class<T> type) {
    final ReflectionInformation reflectionInfo = getRequiredReflectionInfo(type);
    LengthBounds lengthBounds = LengthBounds.ZERO;
    int sszListCount = 0;
    int bitlistCount = 0;
    int vectorCount = 0;
    int bitvectorCount = 0;
    for (Field field : reflectionInfo.getFields()) {
      final Class<?> fieldType = field.getType();
      final LengthBounds fieldLengthBounds;
      if (getOptionalReflectionInfo(fieldType).isPresent()) {
        fieldLengthBounds = calculateLengthBounds(fieldType);

      } else if (fieldType == Bitlist.class) {
        fieldLengthBounds = calculateBitlistLength(reflectionInfo, bitlistCount);
        bitlistCount++;

      } else if (fieldType == SSZList.class) {
        fieldLengthBounds = calculateSszListLength(reflectionInfo, sszListCount);
        sszListCount++;

      } else if (isVector(fieldType)) {
        fieldLengthBounds = calculateSszVectorLength(reflectionInfo, vectorCount);
        vectorCount++;

      } else if (isBitvector(fieldType)) {
        fieldLengthBounds = calculateBitvectorLength(reflectionInfo, bitvectorCount);
        bitvectorCount++;

      } else if (isPrimitive(fieldType)) {
        fieldLengthBounds = new LengthBounds(getPrimitiveLength(fieldType));

      } else {
        throw new IllegalArgumentException(
            "Don't know how to calculate length for " + fieldType.getSimpleName());
      }

      if (isVariable(fieldType)) {
        // The fixed parts includes an offset in place of the variable length value
        lengthBounds = lengthBounds.add(new LengthBounds(BYTES_PER_LENGTH_OFFSET.longValue()));
      }
      lengthBounds = lengthBounds.add(fieldLengthBounds);
    }
    return lengthBounds;
  }

  private static LengthBounds calculateBitvectorLength(
      final ReflectionInformation reflectionInfo, final int bitvectorCount) {
    final LengthBounds fieldLengthBounds;
    final Integer size = reflectionInfo.getBitvectorSizes().get(bitvectorCount);
    final int serializationLength = Bitvector.sszSerializationLength(size);
    fieldLengthBounds = new LengthBounds(serializationLength, serializationLength);
    return fieldLengthBounds;
  }

  private static LengthBounds calculateSszVectorLength(
      final ReflectionInformation reflectionInfo, final int vectorCount) {
    final LengthBounds fieldLengthBounds;
    final Class<?> elementType = reflectionInfo.getVectorElementTypes().get(vectorCount);
    final int vectorLength = reflectionInfo.getVectorLengths().get(vectorCount);
    final LengthBounds elementLengthBounds = getElementLengthBounds(elementType);
    fieldLengthBounds =
        new LengthBounds(
            vectorLength * elementLengthBounds.getMin(),
            vectorLength * elementLengthBounds.getMax());
    return fieldLengthBounds;
  }

  private static LengthBounds calculateSszListLength(
      final ReflectionInformation reflectionInfo, final int variableFieldCount) {
    final LengthBounds fieldLengthBounds;
    final Class<?> listElementType = reflectionInfo.getListElementTypes().get(variableFieldCount);
    final long listElementMaxSize = reflectionInfo.getListElementMaxSizes().get(variableFieldCount);
    final LengthBounds elementLengthBounds = getElementLengthBounds(listElementType);
    final long variableFieldOffsetsLength =
        isVariable(listElementType) ? BYTES_PER_LENGTH_OFFSET.intValue() * listElementMaxSize : 0;
    fieldLengthBounds =
        new LengthBounds(
            0, elementLengthBounds.getMax() * listElementMaxSize + variableFieldOffsetsLength);
    return fieldLengthBounds;
  }

  private static LengthBounds calculateBitlistLength(
      final ReflectionInformation reflectionInfo, final int variableFieldCount) {
    final LengthBounds fieldLengthBounds;
    final long maxSize = reflectionInfo.getBitlistElementMaxSizes().get(variableFieldCount);
    fieldLengthBounds =
        new LengthBounds(
            Bitlist.sszSerializationLength(Math.toIntExact(0)),
            Bitlist.sszSerializationLength(Math.toIntExact(maxSize)));
    return fieldLengthBounds;
  }

  private static LengthBounds getElementLengthBounds(final Class<?> listElementType) {
    if (isPrimitive(listElementType)) {
      final int primitiveLength = getPrimitiveLength(listElementType);
      return new LengthBounds(primitiveLength, primitiveLength);
    }
    return calculateLengthBounds(listElementType);
  }

  private static int getPrimitiveLength(final Class<?> classInfo) {
    switch (classInfo.getSimpleName()) {
      case "UInt64":
        return UInt64.BYTES;
      case "ArrayWrappingBytes32":
      case "Bytes32":
        return Bytes32.SIZE;
      case "Bytes48":
        return Bytes48.SIZE;
      case "Bytes4":
        return Bytes4.SIZE;
      case "BLSSignature":
        return BLSSignature.SSZ_BLS_SIGNATURE_SIZE;
      case "BLSPublicKey":
        return BLSPublicKey.SSZ_BLS_PUBKEY_SIZE;
      case "Boolean":
      case "boolean":
        return BOOLEAN_SIZE;
      default:
        throw new IllegalArgumentException(
            "Unknown length for primitive " + classInfo.getSimpleName());
    }
  }
}
