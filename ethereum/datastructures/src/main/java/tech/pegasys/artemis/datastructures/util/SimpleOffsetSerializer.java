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

package tech.pegasys.artemis.datastructures.util;

import static tech.pegasys.artemis.util.config.Constants.BYTES_PER_LENGTH_OFFSET;

import com.google.common.primitives.UnsignedLong;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import org.apache.tuweni.ssz.SSZReader;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockBody;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.BeaconBlocksByRangeRequestMessage;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.GoodbyeMessage;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.StatusMessage;
import tech.pegasys.artemis.datastructures.operations.AggregateAndProof;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.AttestationData;
import tech.pegasys.artemis.datastructures.operations.AttesterSlashing;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.operations.DepositData;
import tech.pegasys.artemis.datastructures.operations.DepositMessage;
import tech.pegasys.artemis.datastructures.operations.IndexedAttestation;
import tech.pegasys.artemis.datastructures.operations.ProposerSlashing;
import tech.pegasys.artemis.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.artemis.datastructures.operations.VoluntaryExit;
import tech.pegasys.artemis.datastructures.state.BeaconStateImpl;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.datastructures.state.Fork;
import tech.pegasys.artemis.datastructures.state.HistoricalBatch;
import tech.pegasys.artemis.datastructures.state.PendingAttestation;
import tech.pegasys.artemis.datastructures.state.ValidatorImpl;
import tech.pegasys.artemis.util.SSZTypes.Bitlist;
import tech.pegasys.artemis.util.SSZTypes.Bitvector;
import tech.pegasys.artemis.util.SSZTypes.Bytes4;
import tech.pegasys.artemis.util.SSZTypes.SSZContainer;
import tech.pegasys.artemis.util.SSZTypes.SSZList;
import tech.pegasys.artemis.util.SSZTypes.SSZMutableList;
import tech.pegasys.artemis.util.SSZTypes.SSZVector;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.bls.BLSSignature;
import tech.pegasys.artemis.util.sos.ReflectionInformation;
import tech.pegasys.artemis.util.sos.SimpleOffsetSerializable;

@SuppressWarnings({"rawtypes", "unchecked"})
public class SimpleOffsetSerializer {

  public static HashMap<Class, ReflectionInformation> classReflectionInfo = new HashMap<>();

  public static void setConstants() {
    classReflectionInfo.put(
        SignedBeaconBlock.class, new ReflectionInformation(SignedBeaconBlock.class));
    classReflectionInfo.put(BeaconBlock.class, new ReflectionInformation(BeaconBlock.class));
    classReflectionInfo.put(
        BeaconBlockBody.class, new ReflectionInformation(BeaconBlockBody.class));
    classReflectionInfo.put(
        BeaconBlockHeader.class, new ReflectionInformation(BeaconBlockHeader.class));
    classReflectionInfo.put(
        SignedBeaconBlockHeader.class, new ReflectionInformation(SignedBeaconBlockHeader.class));
    classReflectionInfo.put(Eth1Data.class, new ReflectionInformation(Eth1Data.class));
    classReflectionInfo.put(Attestation.class, new ReflectionInformation(Attestation.class));
    classReflectionInfo.put(
        AttestationData.class, new ReflectionInformation(AttestationData.class));
    classReflectionInfo.put(
        AttesterSlashing.class, new ReflectionInformation(AttesterSlashing.class));
    classReflectionInfo.put(Deposit.class, new ReflectionInformation(Deposit.class));
    classReflectionInfo.put(DepositData.class, new ReflectionInformation(DepositData.class));
    classReflectionInfo.put(DepositMessage.class, new ReflectionInformation(DepositMessage.class));
    classReflectionInfo.put(
        IndexedAttestation.class, new ReflectionInformation(IndexedAttestation.class));
    classReflectionInfo.put(
        ProposerSlashing.class, new ReflectionInformation(ProposerSlashing.class));
    classReflectionInfo.put(
        SignedVoluntaryExit.class, new ReflectionInformation(SignedVoluntaryExit.class));
    classReflectionInfo.put(VoluntaryExit.class, new ReflectionInformation(VoluntaryExit.class));
    classReflectionInfo.put(
        BeaconStateImpl.class, new ReflectionInformation(BeaconStateImpl.class));
    classReflectionInfo.put(Checkpoint.class, new ReflectionInformation(Checkpoint.class));
    classReflectionInfo.put(Fork.class, new ReflectionInformation(Fork.class));
    classReflectionInfo.put(
        HistoricalBatch.class, new ReflectionInformation(HistoricalBatch.class));
    classReflectionInfo.put(
        PendingAttestation.class, new ReflectionInformation(PendingAttestation.class));
    classReflectionInfo.put(ValidatorImpl.class, new ReflectionInformation(ValidatorImpl.class));
    classReflectionInfo.put(StatusMessage.class, new ReflectionInformation(StatusMessage.class));
    classReflectionInfo.put(GoodbyeMessage.class, new ReflectionInformation(GoodbyeMessage.class));
    classReflectionInfo.put(
        BeaconBlocksByRangeRequestMessage.class,
        new ReflectionInformation(BeaconBlocksByRangeRequestMessage.class));
    classReflectionInfo.put(
        AggregateAndProof.class, new ReflectionInformation(AggregateAndProof.class));
  }

  static {
    setConstants();
  }

  public static Bytes serialize(SimpleOffsetSerializable value) {
    // TODO assert sum(fixed_lengths + variable_lengths) < 2**(BYTES_PER_LENGTH_OFFSET *
    // BITS_PER_BYTE)
    // List<UnsignedLong> variable_lengths = new ArrayList<>();
    List<UnsignedLong> variable_offsets = new ArrayList<>();
    List<Bytes> interleaved_values = new ArrayList<>();
    UnsignedLong fixedLengthSum = UnsignedLong.ZERO;
    UnsignedLong varLengthSum = UnsignedLong.ZERO;

    // System.out.println("Fixed Part Size: " + value.get_fixed_parts().size());
    // System.out.println("Var Part Size: " + value.get_variable_parts().size());
    for (Bytes fixedPart : value.get_fixed_parts()) {
      UnsignedLong fixedPartSize = UnsignedLong.valueOf(fixedPart.size());
      if (fixedPartSize.equals(UnsignedLong.ZERO)) {
        fixedPartSize = UnsignedLong.valueOf(4L);
      }
      fixedLengthSum = fixedLengthSum.plus(fixedPartSize);
    }

    variable_offsets.add(fixedLengthSum);
    for (Bytes varPart : value.get_variable_parts()) {
      UnsignedLong varPartSize = UnsignedLong.valueOf(varPart.size());
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
    List<UnsignedLong> fixed_lengths = Collections.nCopies(values.size(), BYTES_PER_LENGTH_OFFSET);
    List<Bytes> variable_parts = new ArrayList<>();
    List<Bytes> fixed_parts = new ArrayList<>();
    UnsignedLong offset = UnsignedLong.ZERO;
    for (UnsignedLong length : fixed_lengths) {
      offset = offset.plus(length);
    }
    for (Bytes part : parts) {
      fixed_parts.add(SSZ.encodeUInt32(offset.longValue()));
      variable_parts.add(part);
      offset = offset.plus(UnsignedLong.valueOf(part.size()));
    }
    return Bytes.wrap(
        Bytes.concatenate(fixed_parts.toArray(new Bytes[0])),
        Bytes.concatenate(variable_parts.toArray(new Bytes[0])));
  }

  public static <T> T deserialize(Bytes bytes, Class<T> classInfo) {
    MutableInt bytePointer = new MutableInt(0);
    if (!isPrimitive(classInfo)) {
      return SSZ.decode(
          bytes,
          reader -> deserializeContainerErrorWrapper(classInfo, reader, bytePointer, bytes.size()));
    } else {
      return SSZ.decode(bytes, reader -> (T) deserializePrimitive(classInfo, reader, bytePointer));
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
    ReflectionInformation reflectionInformation = classReflectionInfo.get(classInfo);
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
    for (Integer variableFieldIndex : variableFieldIndices) {
      Class fieldClass = reflectionInformation.getFields()[variableFieldIndex].getType();

      int currentObjectEndByte =
          (variableObjectCounter + 1) == variableFieldIndices.size()
              ? bytesEndByte
              : currentObjectStartByte + offsets.get(variableObjectCounter + 1);
      Object fieldObject = null;
      if (fieldClass == SSZList.class) {
        Class listElementType =
            reflectionInformation.getListElementTypes().get(variableObjectCounter);
        Long listElementMaxSize =
            reflectionInformation.getListElementMaxSizes().get(variableObjectCounter);
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
        fieldObject = newSSZList;
      } else if (fieldClass == Bitlist.class) {
        fieldObject =
            deserializeBitlist(
                reflectionInformation,
                reader,
                bytesPointer,
                variableObjectCounter,
                currentObjectEndByte);

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
    return Bitlist.fromBytes(reader.readFixedBytes(numBytesToRead), bitlistElementMaxSize);
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
      } else if (isContainer(classInfo) && !classReflectionInfo.get(classInfo).isVariable()) {
        newList.add(deserializeFixedContainer(classInfo, reader, bytePointer));
      }
    }
    return SSZVector.createMutable(newList, classInfo);
  }

  public static Object deserializePrimitive(
      Class classInfo, SSZReader reader, MutableInt bytePointer) {
    switch (classInfo.getSimpleName()) {
      case "UnsignedLong":
        bytePointer.add(8);
        return UnsignedLong.fromLongBits(reader.readUInt64());
      case "ArrayWrappingBytes32":
      case "Bytes32":
        bytePointer.add(32);
        return Bytes32.wrap(reader.readFixedBytes(32));
      case "Bytes4":
        bytePointer.add(4);
        return new Bytes4(reader.readFixedBytes(4));
      case "BLSSignature":
        bytePointer.add(96);
        return BLSSignature.fromBytes(reader.readFixedBytes(96));
      case "BLSPublicKey":
        bytePointer.add(48);
        return BLSPublicKey.fromBytes(reader.readFixedBytes(48));
      case "boolean":
        bytePointer.add(1);
        return reader.readBoolean();
      default:
        return new Object();
    }
  }

  private static int readOffset(SSZReader reader, MutableInt bytesPointer) {
    bytesPointer.add(4);
    return reader.readInt32();
  }

  private static boolean isVariable(Class classInfo) {
    if (classInfo == SSZList.class || classInfo == Bitlist.class) {
      return true;
    } else if (classReflectionInfo.get(classInfo) != null) {
      return classReflectionInfo.get(classInfo).isVariable();
    } else {
      return false;
    }
  }

  private static boolean isPrimitive(Class classInfo) {
    return !(SSZContainer.class.isAssignableFrom(classInfo)
        || classInfo == SSZVector.class
        || classInfo == Bitvector.class);
  }

  private static boolean isVector(Class classInfo) {
    return classInfo == SSZVector.class;
  }

  private static boolean isBitvector(Class classInfo) {
    return classInfo == Bitvector.class;
  }

  private static boolean isContainer(Class classInfo) {
    return SSZContainer.class.isAssignableFrom(classInfo);
  }
}
