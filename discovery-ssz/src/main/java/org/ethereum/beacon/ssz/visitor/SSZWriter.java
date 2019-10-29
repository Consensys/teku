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

import static com.google.common.base.Preconditions.checkArgument;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.math.BigInteger;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.ssz.InvalidSSZTypeException;

/** Writer with some adoption of {@link net.consensys.cava.ssz.SSZ} code */
public class SSZWriter {

  private static final Bytes TRUE = Bytes.of((byte) 1);
  private static final Bytes FALSE = Bytes.of((byte) 0);

  private SSZWriter() {}

  /**
   * Encode {@link Bytes}.
   *
   * @param value The value to encode.
   * @return The SSZ encoding in a {@link Bytes} value.
   */
  public static Bytes encodeBytes(Bytes value) {
    return Bytes.wrap(value);
  }

  /**
   * Encode fixed-size bytes value as ssz element
   *
   * @param bytes Bytes data
   * @param byteLength Byte length of ssz element
   * @return the SSZ encoding in a {@link Bytes} value
   */
  public static Bytes encodeBytes(Bytes bytes, int byteLength) {
    if (bytes.size() != byteLength) {
      throw new InvalidSSZTypeException(
          String.format(
              "Error attempt to encode bytes data with length of %s to ssz element with fixed length %s. Lengths must match.",
              bytes.size(), byteLength));
    }
    return bytes;
  }

  /**
   * Encode a value to a {@link Bytes} value.
   *
   * @param value The value to encode.
   * @return The SSZ encoding in a {@link Bytes} value.
   */
  public static Bytes encodeByteArray(byte[] value) {
    return encodeBytes(Bytes.wrap(value));
  }

  /**
   * Encode a string to a {@link Bytes} value.
   *
   * @param str The string to encode.
   * @return The SSZ encoding in a {@link Bytes} value.
   */
  public static Bytes encodeString(String str) {
    return encodeByteArray(str.getBytes(UTF_8));
  }

  /**
   * Encode a big integer to a {@link Bytes} value.
   *
   * @param value the big integer to encode
   * @param bitLength the bit length of the integer value (must be a multiple of 8)
   * @return the SSZ encoding in a {@link Bytes} value
   * @throws IllegalArgumentException if the value is too large for the specified {@code bitLength}
   */
  public static Bytes encodeBigInteger(BigInteger value, int bitLength) {
    return Bytes.wrap(encodeBigIntegerToByteArray(value, bitLength));
  }

  private static byte[] encodeBigIntegerToByteArray(BigInteger value, int bitLength) {
    checkArgument(bitLength % 8 == 0, "bitLength must be a multiple of 8");

    byte[] bytes = value.toByteArray();
    int valueBytes = bytes.length;
    int offset = 0;
    if (value.signum() >= 0 && bytes[0] == 0) {
      valueBytes = bytes.length - 1;
      offset = 1;
    }

    int byteLength = bitLength / 8;
    checkArgument(valueBytes <= byteLength, "value is too large for the desired bitLength");

    byte[] encoded;
    if (valueBytes == byteLength && offset == 0) {
      encoded = bytes;
    } else {
      encoded = new byte[byteLength];
      int padLength = byteLength - valueBytes;
      System.arraycopy(bytes, offset, encoded, padLength, valueBytes);
      if (value.signum() < 0) {
        // Extend the two's-compliment integer by setting all leading bits to 1.
        for (int i = 0; i < padLength; i++) {
          encoded[i] = (byte) 0xFF;
        }
      }
    }
    // reverse the array to make it little endian
    for (int i = 0; i < (encoded.length / 2); i++) {
      byte swapped = encoded[i];
      encoded[i] = encoded[encoded.length - i - 1];
      encoded[encoded.length - i - 1] = swapped;
    }
    return encoded;
  }

  /**
   * Encode an unsigned long integer to a {@link Bytes} value.
   *
   * <p>Note that {@code value} is a native signed long, but will be interpreted as an unsigned
   * value.
   *
   * @param value the long to encode
   * @param bitLength the bit length of the integer value (must be a multiple of 8)
   * @return the SSZ encoding in a {@link Bytes} value
   * @throws IllegalArgumentException if the value is too large for the specified {@code bitLength}
   */
  public static Bytes encodeULong(long value, int bitLength) {
    return Bytes.wrap(encodeULongToByteArray(value, bitLength));
  }

  private static byte[] encodeULongToByteArray(long value, int bitLength) {
    checkArgument(bitLength % 8 == 0, "bitLength must be a multiple of 8");

    int zeros = Long.numberOfLeadingZeros(value);
    int valueBytes = 8 - (zeros / 8);
    checkArgument(zeros == 0 || value >= 0, "Value must be positive or zero");

    int byteLength = bitLength / 8;
    checkArgument(valueBytes <= byteLength, "value is too large for the desired bitLength");

    byte[] encoded = new byte[byteLength];

    int shift = 0;
    for (int i = 0; i < valueBytes; i++) {
      encoded[i] = (byte) ((value >> shift) & 0xFF);
      shift += 8;
    }
    return encoded;
  }

  /**
   * Encode a boolean to a {@link Bytes} value.
   *
   * @param value the boolean to encode
   * @return the SSZ encoding in a {@link Bytes} value
   */
  public static Bytes encodeBoolean(boolean value) {
    return value ? TRUE : FALSE;
  }
}
