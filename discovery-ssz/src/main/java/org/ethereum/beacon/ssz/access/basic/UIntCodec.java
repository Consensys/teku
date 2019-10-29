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

package org.ethereum.beacon.ssz.access.basic;

import static java.util.function.Function.identity;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.ssz.SSZException;
import org.ethereum.beacon.ssz.SSZSchemeException;
import org.ethereum.beacon.ssz.access.SSZBasicAccessor;
import org.ethereum.beacon.ssz.access.SSZField;
import org.ethereum.beacon.ssz.visitor.SSZReader;
import org.ethereum.beacon.ssz.visitor.SSZWriter;
import tech.pegasys.artemis.util.bytes.BytesValue;
import tech.pegasys.artemis.util.uint.UInt16;
import tech.pegasys.artemis.util.uint.UInt24;
import tech.pegasys.artemis.util.uint.UInt256;
import tech.pegasys.artemis.util.uint.UInt32;
import tech.pegasys.artemis.util.uint.UInt64;
import tech.pegasys.artemis.util.uint.UInt8;

/**
 * SSZ Codec designed to work with fixed numeric data classes, check list in {@link
 * #getSupportedClasses()}
 */
public class UIntCodec implements SSZBasicAccessor {
  private static Map<Class, NumericType> classToNumericType = new HashMap<>();
  private static Set<String> supportedTypes = new HashSet<>();
  private static Set<Class> supportedClassTypes = new HashSet<>();

  static {
    classToNumericType.put(UInt8.class, NumericType.of(Type.LONG, 8));
    classToNumericType.put(UInt16.class, NumericType.of(Type.LONG, 16));
    classToNumericType.put(UInt24.class, NumericType.of(Type.LONG, 24));
    classToNumericType.put(UInt32.class, NumericType.of(Type.LONG, 32));
    classToNumericType.put(UInt64.class, NumericType.of(Type.LONG, 64));
    classToNumericType.put(UInt256.class, NumericType.of(Type.BIGINT, 256));
  }

  static {
  }

  static {
    supportedClassTypes.add(UInt8.class);
    supportedClassTypes.add(UInt16.class);
    supportedClassTypes.add(UInt24.class);
    supportedClassTypes.add(UInt32.class);
    supportedClassTypes.add(UInt64.class);
    supportedClassTypes.add(UInt256.class);
  }

  private static void writeBytes(Bytes value, OutputStream result) {
    try {
      result.write(value.toArrayUnsafe());
    } catch (IOException e) {
      throw new SSZException(String.format("Failed to write value \"%s\" to stream", value), e);
    }
  }

  private static void writeBytesValue(BytesValue value, OutputStream result) {
    try {
      result.write(value.getArrayUnsafe());
    } catch (IOException e) {
      throw new SSZException(String.format("Failed to write value \"%s\" to stream", value), e);
    }
  }

  @Override
  public Set<String> getSupportedSSZTypes() {
    return supportedTypes;
  }

  @Override
  public Set<Class> getSupportedClasses() {
    return supportedClassTypes;
  }

  @Override
  public int getSize(SSZField field) {
    return parseFieldType(field).size / 8;
  }

  @Override
  public void encode(Object value, SSZField field, OutputStream result) {
    NumericType numericType = parseFieldType(field);

    switch (numericType.size) {
      case 8:
        {
          UInt8 uValue = (UInt8) value;
          Bytes bytes = SSZWriter.encodeULong(uValue.getValue(), numericType.size);
          writeBytes(bytes, result);
          break;
        }
      case 16:
        {
          UInt16 uValue = (UInt16) value;
          Bytes bytes = SSZWriter.encodeULong(uValue.getValue(), numericType.size);
          writeBytes(bytes, result);
          break;
        }
      case 24:
        {
          UInt24 uValue = (UInt24) value;
          Bytes bytes = SSZWriter.encodeULong(uValue.getValue(), numericType.size);
          writeBytes(bytes, result);
          break;
        }
      case 32:
        {
          UInt32 uValue = (UInt32) value;
          Bytes bytes = SSZWriter.encodeULong(uValue.getValue(), numericType.size);
          writeBytes(bytes, result);
          break;
        }
      case 64:
        {
          UInt64 uValue = (UInt64) value;
          Bytes bytes = SSZWriter.encodeULong(uValue.getValue(), numericType.size);
          writeBytes(bytes, result);
          break;
        }
      case 256:
        {
          UInt256 uValue = (UInt256) value;
          writeBytesValue(uValue.bytes(), result);
          break;
        }
      default:
        {
          throw new SSZException(String.format("Failed to write value \"%s\" to stream", value));
        }
    }
  }

  @Override
  public Object decode(SSZField field, SSZReader reader) {
    NumericType numericType = parseFieldType(field);
    switch (numericType.type) {
      case LONG:
        {
          return decodeLong(numericType, reader);
        }
      case BIGINT:
        {
          return decodeBigInt(numericType, reader);
        }
    }

    return throwUnsupportedType(field);
  }

  private Object decodeLong(NumericType type, SSZReader reader) {
    // XXX: reader.readULong is buggy
    switch (type.size) {
      case 8:
        {
          return UInt8.valueOf(reader.readUnsignedBigInteger(type.size).intValue());
        }
      case 16:
        {
          return UInt16.valueOf(reader.readUnsignedBigInteger(type.size).intValue());
        }
      case 24:
        {
          return UInt24.valueOf(reader.readUnsignedBigInteger(type.size).intValue());
        }
      case 32:
        {
          return UInt32.valueOf(reader.readUnsignedBigInteger(type.size).intValue());
        }
      case 64:
        {
          return UInt64.valueOf(reader.readUnsignedBigInteger(type.size).longValue());
        }
    }
    String error = String.format("Unsupported type \"%s\"", type);
    throw new SSZSchemeException(error);
  }

  private Object decodeBigInt(NumericType type, SSZReader reader) {
    switch (type.size) {
      case 256:
        {
          return UInt256.wrap(
              BytesValue.of(reader.readUnsignedBigInteger(type.size).toByteArray()));
        }
      default:
        {
          String error = String.format("Unsupported type \"%s\"", type);
          throw new SSZSchemeException(error);
        }
    }
  }

  private NumericType parseFieldType(SSZField field) {
    return classToNumericType.get(field.getRawClass());
  }

  enum Type {
    LONG("long"),
    BIGINT("bigint");

    private static final Map<String, Type> ENUM_MAP;

    static {
      ENUM_MAP = Stream.of(Type.values()).collect(Collectors.toMap(e -> e.type, identity()));
    }

    private String type;

    Type(String type) {
      this.type = type;
    }

    static Type fromValue(String type) {
      return ENUM_MAP.get(type);
    }

    @Override
    public String toString() {
      return type;
    }
  }

  static class NumericType {
    final Type type;
    final int size;

    NumericType(Type type, int size) {
      this.type = type;
      this.size = size;
    }

    static NumericType of(Type type, int size) {
      NumericType res = new NumericType(type, size);
      return res;
    }
  }
}
