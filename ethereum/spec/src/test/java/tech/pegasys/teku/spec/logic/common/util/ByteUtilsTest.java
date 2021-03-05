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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class ByteUtilsTest {

  @Test
  void intToBytes32UInt64() {
    assertEquals(
        Bytes32.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000000"),
        ByteUtils.uintToBytes32(UInt64.ZERO));
    assertEquals(
        Bytes32.fromHexString("0x0100000000000000000000000000000000000000000000000000000000000000"),
        ByteUtils.uintToBytes32(UInt64.ONE));
    assertEquals(
        Bytes32.fromHexString("0xffffffffffffffff000000000000000000000000000000000000000000000000"),
        ByteUtils.uintToBytes32(UInt64.MAX_VALUE));
    assertEquals(
        Bytes32.fromHexString("0xefcdab8967452301000000000000000000000000000000000000000000000000"),
        ByteUtils.uintToBytes32(UInt64.valueOf(0x0123456789abcdefL)));
  }

  @Test
  void bytesToInt() {
    assertEquals(UInt64.valueOf(0), ByteUtils.bytesToUInt64(Bytes.fromHexString("0x00")));
    assertEquals(UInt64.valueOf(1), ByteUtils.bytesToUInt64(Bytes.fromHexString("0x01")));
    assertEquals(
        UInt64.valueOf(1), ByteUtils.bytesToUInt64(Bytes.fromHexString("0x0100000000000000")));
    assertEquals(
        UInt64.valueOf(0x123456789abcdef0L),
        ByteUtils.bytesToUInt64(Bytes.fromHexString("0xf0debc9a78563412")));
    assertEquals(
        UInt64.fromLongBits(0xffffffffffffffffL),
        ByteUtils.bytesToUInt64(Bytes.fromHexString("0xffffffffffffffff")));
    assertEquals(
        UInt64.fromLongBits(0x0000000000000080L),
        ByteUtils.bytesToUInt64(Bytes.fromHexString("0x8000000000000000")));
    assertEquals(
        UInt64.fromLongBits(0xffffffffffffff7fL),
        ByteUtils.bytesToUInt64(Bytes.fromHexString("0x7fffffffffffffff")));
  }
}
