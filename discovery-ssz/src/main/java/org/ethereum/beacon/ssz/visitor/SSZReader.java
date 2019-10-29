/*
 * Copyright 2018 ConsenSys AG.
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
import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.math.BigInteger;
import java.util.function.Supplier;
import net.consensys.cava.bytes.Bytes;
import net.consensys.cava.ssz.EndOfSSZException;
import net.consensys.cava.ssz.InvalidSSZTypeException;
import org.javatuples.Pair;

/** Reader with some adoption of {@link net.consensys.cava.ssz.BytesSSZReader} code */
public class SSZReader {

  private final Bytes content;
  private int index = 0;

  public SSZReader(Bytes bytes) {
    this.content = bytes;
  }

  public Bytes readBytes() {
    return readHash(content.size() - index);
  }

  public String readString() {
    return new String(readBytes().toArray(), UTF_8);
  }

  public int readUInt(int bitLength) {
    Pair<Bytes, Integer> params =
        checkAndConsumeNumber(bitLength, Integer.BYTES, "decoded integer is too large for an int");
    return params
        .getValue0()
        .slice(0, params.getValue0().size() - params.getValue1())
        .toInt(LITTLE_ENDIAN);
  }

  private Pair<Bytes, Integer> checkAndConsumeNumber(
      int bitLength, int maxByteSize, String errorMessage) {
    checkArgument(
        bitLength % Byte.SIZE == 0, String.format("bitLength must be a multiple of %s", Byte.SIZE));
    int byteLength = bitLength / Byte.SIZE;
    ensureBytes(
        byteLength,
        () -> "SSZ encoded data has insufficient length to read a " + bitLength + "-bit numeric");
    Bytes bytes = consumeBytes(byteLength);
    int zeroBytes = bytes.numberOfTrailingZeroBytes();
    if ((byteLength - zeroBytes) > maxByteSize) {
      throw new InvalidSSZTypeException(errorMessage);
    }

    return Pair.with(bytes, zeroBytes);
  }

  private Bytes consumeBytes(int size) {
    Bytes bytes = content.slice(index, size);
    index += size;
    return bytes;
  }

  public long readULong(int bitLength) {
    Pair<Bytes, Integer> params =
        checkAndConsumeNumber(bitLength, Long.BYTES, "decoded number is too large for an long");
    return params
        .getValue0()
        .slice(0, params.getValue0().size() - params.getValue1())
        .toLong(LITTLE_ENDIAN);
  }

  public boolean readBoolean() {
    int value = readUInt(Byte.SIZE);
    if (value == 0) {
      return false;
    } else if (value == 1) {
      return true;
    } else {
      throw new InvalidSSZTypeException("decoded value is not a boolean");
    }
  }

  public BigInteger readUnsignedBigInteger(int bitLength) {
    checkArgument(bitLength % 8 == 0, "bitLength must be a multiple of 8");
    int byteLength = bitLength / 8;
    ensureBytes(
        byteLength,
        () -> "SSZ encoded data has insufficient length to read a " + bitLength + "-bit integer");
    return consumeBytes(byteLength).toUnsignedBigInteger(LITTLE_ENDIAN);
  }

  public Bytes readHash(int hashLength) {
    ensureBytes(
        hashLength,
        () -> "SSZ encoded data has insufficient length to read a " + hashLength + "-byte hash");
    return consumeBytes(hashLength);
  }

  private void ensureBytes(int byteLength, Supplier<String> message) {
    if (index == content.size() && byteLength != 0) {
      throw new EndOfSSZException();
    }
    if (content.size() - index - byteLength < 0) {
      throw new InvalidSSZTypeException(message.get());
    }
  }
}
