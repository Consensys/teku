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

package tech.pegasys.teku.datastructures.util;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.util.config.Constants.BYTES_PER_LENGTH_OFFSET;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import org.apache.tuweni.ssz.SSZ;
import org.apache.tuweni.ssz.SSZReader;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockBody;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.BeaconBlocksByRangeRequestMessage;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.EmptyMessage;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.EnrForkId;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.GoodbyeMessage;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.MetadataMessage;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.PingMessage;
import tech.pegasys.teku.datastructures.networking.libp2p.rpc.StatusMessage;
import tech.pegasys.teku.datastructures.operations.AggregateAndProof;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.AttestationData;
import tech.pegasys.teku.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.datastructures.operations.Deposit;
import tech.pegasys.teku.datastructures.operations.DepositData;
import tech.pegasys.teku.datastructures.operations.DepositMessage;
import tech.pegasys.teku.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.datastructures.operations.SignedAggregateAndProof;
import tech.pegasys.teku.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.datastructures.operations.VoluntaryExit;
import tech.pegasys.teku.datastructures.state.BeaconStateImpl;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.state.Fork;
import tech.pegasys.teku.datastructures.state.ForkData;
import tech.pegasys.teku.datastructures.state.HistoricalBatch;
import tech.pegasys.teku.datastructures.state.PendingAttestation;
import tech.pegasys.teku.datastructures.state.SigningData;
import tech.pegasys.teku.datastructures.state.Validator;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.Bitlist;
import tech.pegasys.teku.ssz.SSZTypes.Bitvector;
import tech.pegasys.teku.ssz.SSZTypes.Bytes4;
import tech.pegasys.teku.ssz.SSZTypes.SSZContainer;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;
import tech.pegasys.teku.ssz.SSZTypes.SSZMutableList;
import tech.pegasys.teku.ssz.SSZTypes.SSZVector;
import tech.pegasys.teku.ssz.backing.ViewRead;
import tech.pegasys.teku.ssz.backing.type.ViewType;
import tech.pegasys.teku.ssz.sos.ReflectionInformation;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;
import tech.pegasys.teku.ssz.sos.SszReader;

@SuppressWarnings({"rawtypes", "unchecked"})
public class SimpleOffsetSerializer {

  static final int BOOLEAN_SIZE = 1;
  public static HashMap<Class, ReflectionInformation> classReflectionInfo = new HashMap<>();
  public static HashMap<Class, LengthBounds> classLengthBounds = new HashMap<>();

  public static void setConstants() {
    List<Class> classes =
        List.of(
            SignedBeaconBlock.class,
            BeaconBlock.class,
            BeaconBlockBody.class,
            BeaconBlockHeader.class,
            SignedBeaconBlockHeader.class,
            Eth1Data.class,
            Attestation.class,
            AttestationData.class,
            AttesterSlashing.class,
            Deposit.class,
            DepositData.class,
            DepositMessage.class,
            IndexedAttestation.class,
            ProposerSlashing.class,
            SignedVoluntaryExit.class,
            VoluntaryExit.class,
            BeaconStateImpl.class,
            Checkpoint.class,
            Fork.class,
            HistoricalBatch.class,
            PendingAttestation.class,
            Validator.class,
            StatusMessage.class,
            GoodbyeMessage.class,
            BeaconBlocksByRangeRequestMessage.class,
            AggregateAndProof.class,
            SignedAggregateAndProof.class,
            ForkData.class,
            EnrForkId.class,
            VoteTracker.class,
            MetadataMessage.class,
            EmptyMessage.class,
            PingMessage.class,
            SigningData.class);

    for (Class classItem : classes) {
      classReflectionInfo.put(classItem, new ReflectionInformation(classItem));
    }

    for (Class classItem : classes) {
      classLengthBounds.put(classItem, LengthBoundCalculator.calculateLengthBounds(classItem));
    }
  }

  static {
    setConstants();
  }

  public static Bytes serialize(SimpleOffsetSerializable value) {
    if (value instanceof ViewRead) {
      return ((ViewRead) value).sszSerialize();
    }

    List<UInt64> variable_offsets = new ArrayList<>();
    List<Bytes> interleaved_values = new ArrayList<>();
    UInt64 fixedLengthSum = UInt64.ZERO;
    UInt64 varLengthSum = UInt64.ZERO;

    for (Bytes fixedPart : value.get_fixed_parts()) {
      UInt64 fixedPartSize = UInt64.valueOf(fixedPart.size());
      if (fixedPartSize.equals(UInt64.ZERO)) {
        fixedPartSize = UInt64.valueOf(4L);
      }
      fixedLengthSum = fixedLengthSum.plus(fixedPartSize);
    }

    variable_offsets.add(fixedLengthSum);
    for (Bytes varPart : value.get_variable_parts()) {
      UInt64 varPartSize = UInt64.valueOf(varPart.size());
      varLengthSum = varLengthSum.plus(varPartSize);
      variable_offsets.add(fixedLengthSum.plus(varLengthSum));
    }

    int interleavingIndex = 0;
    for (Bytes element : value.get_fixed_parts()) {
      if (!element.equals(Bytes.EMPTY)) {
        interleaved_values.add(element);
      } else {
        interleaved_values.add(
            SSZ.encodeUInt32(variable_offsets.get(interleavingIndex).longValue()));
      }
      ++interleavingIndex;
    }

    return Bytes.wrap(
        Bytes.concatenate(interleaved_values.toArray(new Bytes[0])),
        Bytes.concatenate(value.get_variable_parts().toArray(new Bytes[0])));
  }

  public static Bytes serializeFixedCompositeList(
      SSZList<? extends SimpleOffsetSerializable> values) {
    return Bytes.fromHexString(
        values.stream()
            .map(item -> serialize(item).toHexString().substring(2))
            .collect(Collectors.joining()));
  }

  public static Bytes serializeVariableCompositeList(
      SSZList<? extends SimpleOffsetSerializable> values) {
    List<Bytes> parts =
        values.stream().map(SimpleOffsetSerializer::serialize).collect(Collectors.toList());
    List<UInt64> fixed_lengths = Collections.nCopies(values.size(), BYTES_PER_LENGTH_OFFSET);
    List<Bytes> variable_parts = new ArrayList<>();
    List<Bytes> fixed_parts = new ArrayList<>();
    UInt64 offset = UInt64.ZERO;
    for (UInt64 length : fixed_lengths) {
      offset = offset.plus(length);
    }
    for (Bytes part : parts) {
      fixed_parts.add(SSZ.encodeUInt32(offset.longValue()));
      variable_parts.add(part);
      offset = offset.plus(part.size());
    }
    return Bytes.wrap(
        Bytes.concatenate(fixed_parts.toArray(new Bytes[0])),
        Bytes.concatenate(variable_parts.toArray(new Bytes[0])));
  }

  public static <T> T deserialize(Bytes bytes, Class<T> classInfo) {
    MutableInt bytePointer = new MutableInt(0);
    if (!isPrimitive(classInfo)) {
      Optional<ViewType<?>> maybeViewType = ViewType.getType(classInfo);
      if (maybeViewType.isPresent()) {
        return (T) deserialize(bytes, maybeViewType.get());
      } else {
        return SSZ.decode(
            bytes,
            reader -> {
              final T result =
                  deserializeContainerErrorWrapper(classInfo, reader, bytePointer, bytes.size());
              assertAllDataRead(reader);
              return result;
            });
      }
    } else {
      return SSZ.decode(
          bytes,
          reader -> {
            final T result = (T) deserializePrimitive(classInfo, reader, bytePointer);
            assertAllDataRead(reader);
            return result;
          });
    }
  }

  private static ViewRead deserialize(Bytes bytes, ViewType<?> sszViewType) {
    try (SszReader sszReader = SszReader.fromBytes(bytes)) {
      return sszViewType.sszDeserialize(sszReader);
    }
  }

  public static <T> Optional<LengthBounds> getLengthBounds(final Class<T> type) {
    return Optional.ofNullable(classLengthBounds.get(type));
  }

  private static void assertAllDataRead(SSZReader reader) {
    if (!reader.isComplete()) {
      throw new IllegalStateException("Unread data detected.");
    }
  }

  private static <T> T deserializeContainerErrorWrapper(
      Class<T> classInfo, SSZReader reader, MutableInt bytePointer, int bytesEndByte) {
    try {
      return deserializeContainer(classInfo, reader, bytePointer, bytesEndByte);
    } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
      System.out.println(
          "Deserialization error with class: "
              + classInfo.getSimpleName()
              + "\n Error: "
              + e.toString());
    }
    return null;
  }

  private static <T> T deserializeContainer(
      Class<T> classInfo, SSZReader reader, MutableInt bytesPointer, int bytesEndByte)
      throws InstantiationException, IllegalAccessException, InvocationTargetException {
    int currentObjectStartByte = bytesPointer.intValue();
    ReflectionInformation reflectionInformation = getRequiredReflectionInfo(classInfo);
    List<Integer> offsets = new ArrayList<>();
    List<Integer> variableFieldIndices = new ArrayList<>();

    Object[] params =
        deserializeFixedParts(
            reflectionInformation, reader, bytesPointer, offsets, variableFieldIndices);

    if (isVariable(classInfo)) {
      deserializeVariableParts(
          reflectionInformation,
          reader,
          bytesPointer,
          variableFieldIndices,
          offsets,
          currentObjectStartByte,
          params,
          bytesEndByte);
    }

    for (int i = 0; i < params.length; i++) {
      if (params[i] == null) {
        throw new InstantiationException("Parameter is null: " + i);
      }
    }
    Constructor constructor = reflectionInformation.getConstructor();
    return (T) constructor.newInstance(params);
  }

  private static <T> T deserializeFixedContainer(
      Class<T> classInfo, SSZReader reader, MutableInt bytePointer)
      throws InstantiationException, InvocationTargetException, IllegalAccessException {
    // bytesEndByte is only necessary for variable size containers
    return deserializeContainer(classInfo, reader, bytePointer, 0);
  }

  private static Object[] deserializeFixedParts(
      ReflectionInformation reflectionInformation,
      SSZReader reader,
      MutableInt bytesPointer,
      List<Integer> offsets,
      List<Integer> variableFieldIndices)
      throws InstantiationException, InvocationTargetException, IllegalAccessException {

    int numParams = reflectionInformation.getParameterCount();
    Field[] fields = reflectionInformation.getFields();
    Object[] params = new Object[numParams];
    int vectorCounter = 0;
    int bitvectorCounter = 0;

    for (int i = 0; i < numParams; i++) {
      Class fieldClass = fields[i].getType();
      Object fieldObject = null;
      if (isVariable(fieldClass)) {
        variableFieldIndices.add(i);
        offsets.add(readOffset(reader, bytesPointer));
      } else if (isPrimitive(fieldClass)) {
        fieldObject = deserializePrimitive(fieldClass, reader, bytesPointer);
      } else if (isContainer(fieldClass)) {
        fieldObject = deserializeFixedContainer(fieldClass, reader, bytesPointer);
      } else if (isVector(fieldClass)) {
        fieldObject =
            deserializeFixedElementVector(
                reflectionInformation.getVectorElementTypes().get(vectorCounter),
                reflectionInformation.getVectorLengths().get(vectorCounter),
                reader,
                bytesPointer);
        vectorCounter++;
      } else if (isBitvector(fieldClass)) {
        fieldObject =
            deserializeBitvector(
                reflectionInformation.getBitvectorSizes().get(bitvectorCounter),
                reader,
                bytesPointer);
        bitvectorCounter++;
      }
      params[i] = fieldObject;
    }
    return params;
  }

  private static void deserializeVariableParts(
      ReflectionInformation reflectionInformation,
      SSZReader reader,
      MutableInt bytesPointer,
      List<Integer> variableFieldIndices,
      List<Integer> offsets,
      int currentObjectStartByte,
      Object[] params,
      int bytesEndByte)
      throws InstantiationException, InvocationTargetException, IllegalAccessException {

    int variableObjectCounter = 0;
    int bitlistCounter = 0;
    int sszListCounter = 0;
    for (Integer variableFieldIndex : variableFieldIndices) {
      Class fieldClass = reflectionInformation.getFields()[variableFieldIndex].getType();

      int currentObjectEndByte =
          (variableObjectCounter + 1) == variableFieldIndices.size()
              ? bytesEndByte
              : currentObjectStartByte + offsets.get(variableObjectCounter + 1);
      Object fieldObject = null;
      if (fieldClass == SSZList.class) {
        Class listElementType = reflectionInformation.getListElementTypes().get(sszListCounter);
        Long listElementMaxSize =
            reflectionInformation.getListElementMaxSizes().get(sszListCounter);
        SSZMutableList newSSZList = SSZList.createMutable(listElementType, listElementMaxSize);
        if (!isVariable(listElementType)) {
          // If SSZList element is fixed size
          deserializeFixedElementList(
              reader, bytesPointer, listElementType, currentObjectEndByte, newSSZList);

        } else {
          // If SSZList element is variable size
          deserializeVariableElementList(
              reader, bytesPointer, listElementType, currentObjectEndByte, newSSZList);
        }
        sszListCounter++;
        fieldObject = newSSZList;
      } else if (fieldClass == Bitlist.class) {
        fieldObject =
            deserializeBitlist(
                reflectionInformation, reader, bytesPointer, bitlistCounter, currentObjectEndByte);
        bitlistCounter++;

      } else if (isContainer(fieldClass)) {
        fieldObject = deserializeContainer(fieldClass, reader, bytesPointer, currentObjectEndByte);
      }
      variableObjectCounter++;
      params[variableFieldIndex] = fieldObject;
    }
  }

  private static Bitvector deserializeBitvector(
      int bitvectorSize, SSZReader reader, MutableInt bytesPointer) {
    int bitvectorByteSize = (bitvectorSize + 7) / 8;
    bytesPointer.add(bitvectorByteSize);
    return Bitvector.fromBytes(reader.readFixedBytes(bitvectorByteSize), bitvectorSize);
  }

  private static Bitlist deserializeBitlist(
      ReflectionInformation reflectionInformation,
      SSZReader reader,
      MutableInt bytesPointer,
      int variableObjectCounter,
      int currentObjectEndByte) {
    Long bitlistElementMaxSize =
        reflectionInformation.getBitlistElementMaxSizes().get(variableObjectCounter);
    int numBytesToRead = currentObjectEndByte - bytesPointer.intValue();
    bytesPointer.add(numBytesToRead);
    return Bitlist.fromSszBytes(reader.readFixedBytes(numBytesToRead), bitlistElementMaxSize);
  }

  private static void deserializeVariableElementList(
      SSZReader reader,
      MutableInt bytesPointer,
      Class listElementType,
      int bytesEndByte,
      SSZMutableList newSSZList)
      throws InstantiationException, InvocationTargetException, IllegalAccessException {

    int currentObjectStartByte = bytesPointer.intValue();

    if (currentObjectStartByte == bytesEndByte) {
      return;
    }

    List<Integer> offsets = new ArrayList<>();

    int variablePartStartByte = currentObjectStartByte + readOffset(reader, bytesPointer);
    offsets.add(variablePartStartByte);

    while (bytesPointer.intValue() < variablePartStartByte) {
      offsets.add(readOffset(reader, bytesPointer));
    }

    for (int i = 0; i < offsets.size(); i++) {
      // Get the end byte of current variable size container either using offset
      // or the end of the outer object you're in
      int currentObjectEndByte =
          (i + 1) == offsets.size() ? bytesEndByte : currentObjectStartByte + offsets.get(i + 1);
      newSSZList.add(
          deserializeContainer(listElementType, reader, bytesPointer, currentObjectEndByte));
    }
  }

  private static void deserializeFixedElementList(
      SSZReader reader,
      MutableInt bytesPointer,
      Class listElementType,
      int currentObjectEndByte,
      SSZMutableList newSSZList)
      throws InstantiationException, InvocationTargetException, IllegalAccessException {
    while (bytesPointer.intValue() < currentObjectEndByte) {
      if (isContainer(listElementType)) {
        newSSZList.add(deserializeFixedContainer(listElementType, reader, bytesPointer));
      } else if (isPrimitive(listElementType)) {
        newSSZList.add(deserializePrimitive(listElementType, reader, bytesPointer));
      }
    }
  }

  private static SSZVector deserializeFixedElementVector(
      Class classInfo, int numElements, SSZReader reader, MutableInt bytePointer)
      throws InstantiationException, InvocationTargetException, IllegalAccessException {
    List newList = new ArrayList<>();
    for (int i = 0; i < numElements; i++) {
      if (isPrimitive(classInfo)) {
        newList.add(deserializePrimitive(classInfo, reader, bytePointer));
      } else if (isContainer(classInfo) && !getRequiredReflectionInfo(classInfo).isVariable()) {
        newList.add(deserializeFixedContainer(classInfo, reader, bytePointer));
      }
    }
    return SSZVector.createMutable(newList, classInfo);
  }

  static ReflectionInformation getRequiredReflectionInfo(Class classInfo) {
    final ReflectionInformation reflectionInfo = classReflectionInfo.get(classInfo);
    checkArgument(
        reflectionInfo != null,
        "Unable to find reflection information for class " + classInfo.getSimpleName());
    return reflectionInfo;
  }

  static Optional<ReflectionInformation> getOptionalReflectionInfo(Class classInfo) {
    return Optional.ofNullable(classReflectionInfo.get(classInfo));
  }

  private static Object deserializePrimitive(
      Class classInfo, SSZReader reader, MutableInt bytePointer) {
    switch (classInfo.getSimpleName()) {
      case "UInt64":
        bytePointer.add(UInt64.BYTES);
        return UInt64.fromLongBits(reader.readUInt64());
      case "ArrayWrappingBytes32":
      case "Bytes32":
        bytePointer.add(Bytes32.SIZE);
        return Bytes32.wrap(reader.readFixedBytes(Bytes32.SIZE));
      case "Bytes48":
        bytePointer.add(Bytes48.SIZE);
        return Bytes48.wrap(reader.readFixedBytes(Bytes48.SIZE));
      case "Bytes4":
        bytePointer.add(Bytes4.SIZE);
        return new Bytes4(reader.readFixedBytes(Bytes4.SIZE));
      case "BLSSignature":
        bytePointer.add(BLSSignature.SSZ_BLS_SIGNATURE_SIZE);
        return BLSSignature.fromSSZBytes(
            reader.readFixedBytes(BLSSignature.SSZ_BLS_SIGNATURE_SIZE));
      case "BLSPublicKey":
        bytePointer.add(BLSPublicKey.SSZ_BLS_PUBKEY_SIZE);
        return BLSPublicKey.fromSSZBytes(reader.readFixedBytes(BLSPublicKey.SSZ_BLS_PUBKEY_SIZE));
      case "Boolean":
      case "boolean":
        bytePointer.add(BOOLEAN_SIZE);
        return reader.readBoolean();
      default:
        throw new IllegalArgumentException("Unable to deserialize " + classInfo.getSimpleName());
    }
  }

  private static int readOffset(SSZReader reader, MutableInt bytesPointer) {
    bytesPointer.add(4);
    return reader.readInt32();
  }

  static boolean isVariable(Class classInfo) {
    if (classInfo == SSZList.class || classInfo == Bitlist.class) {
      return true;
    } else {
      return getOptionalReflectionInfo(classInfo)
          .map(ReflectionInformation::isVariable)
          .orElse(false);
    }
  }

  static boolean isPrimitive(Class classInfo) {
    return !(SSZContainer.class.isAssignableFrom(classInfo)
        || classInfo == SSZVector.class
        || classInfo == Bitvector.class
        || classInfo == VoteTracker.class);
  }

  static boolean isVector(Class classInfo) {
    return classInfo == SSZVector.class;
  }

  static boolean isBitvector(Class classInfo) {
    return classInfo == Bitvector.class;
  }

  private static boolean isContainer(Class classInfo) {
    return SSZContainer.class.isAssignableFrom(classInfo);
  }
}
