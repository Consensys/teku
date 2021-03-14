/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.spec.logic.common.util;

import java.nio.ByteOrder;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class ByteUtils {
  public static Bytes uintToBytes(long value, int numBytes) {
    int longBytes = Long.SIZE / 8;
    Bytes valueBytes = Bytes.ofUnsignedLong(value, ByteOrder.LITTLE_ENDIAN);
    if (numBytes <= longBytes) {
      return valueBytes.slice(0, numBytes);
    } else {
      return Bytes.wrap(valueBytes, Bytes.wrap(new byte[numBytes - longBytes]));
    }
  }

  public static Bytes32 uintToBytes32(long value) {
    return Bytes32.wrap(uintToBytes(value, 32));
  }

  static Bytes32 uintToBytes32(UInt64 value) {
    return uintToBytes32(value.longValue());
  }

  public static UInt64 bytesToUInt64(Bytes data) {
    return UInt64.fromLongBits(data.toLong(ByteOrder.LITTLE_ENDIAN));
  }
}
