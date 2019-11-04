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

package org.ethereum.beacon.discovery;

// import static org.junit.Assert.assertEquals;

// import org.junit.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.ECKeyPair;

// import tech.pegasys.artemis.util.bytes.BytesValue;
// import tech.pegasys.artemis.util.bytes.BytesValue;

public class FunctionsTest {
  private final BytesValue testKey1 =
      BytesValue.fromHexString("3332ca2b7003810449b6e596c3d284e914a1a51c9f76e4d9d7d43ef84adf6ed6");
  private final BytesValue testKey2 =
      BytesValue.fromHexString("66fb62bfbd66b9177a138c1e5cddbe4f7c30c343e94e68df8769459cb1cde628");
  private BytesValue nodeId1;
  private BytesValue nodeId2;

  public FunctionsTest() {
    byte[] homeNodeIdBytes = new byte[32];
    homeNodeIdBytes[0] = 0x01;
    byte[] destNodeIdBytes = new byte[32];
    destNodeIdBytes[0] = 0x02;
    this.nodeId1 = BytesValue.wrap(homeNodeIdBytes);
    this.nodeId2 = BytesValue.wrap(destNodeIdBytes);
  }

  @Test
  public void testLogDistance() {
    BytesValue nodeId0 =
        BytesValue.fromHexString(
            "0000000000000000000000000000000000000000000000000000000000000000");
    BytesValue nodeId1a =
        BytesValue.fromHexString(
            "0000000000000000000000000000000000000000000000000000000000000001");
    BytesValue nodeId1b =
        BytesValue.fromHexString(
            "1000000000000000000000000000000000000000000000000000000000000000");
    BytesValue nodeId1s =
        BytesValue.fromHexString(
            "1111111111111111111111111111111111111111111111111111111111111111");
    BytesValue nodeId9s =
        BytesValue.fromHexString(
            "9999999999999999999999999999999999999999999999999999999999999999");
    BytesValue nodeIdfs =
        BytesValue.fromHexString(
            "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff");
    assertEquals(0, Functions.logDistance(nodeId1a, nodeId1a));
    assertEquals(1, Functions.logDistance(nodeId0, nodeId1a));
    // So it's big endian
    assertEquals(253, Functions.logDistance(nodeId0, nodeId1b));
    assertEquals(253, Functions.logDistance(nodeId0, nodeId1s));
    assertEquals(256, Functions.logDistance(nodeId0, nodeId9s));
    // maximum distance
    assertEquals(256, Functions.logDistance(nodeId0, nodeIdfs));
    // logDistance is not an additive function
    assertEquals(255, Functions.logDistance(nodeId9s, nodeIdfs));
  }

  @Test
  public void hkdfExpandTest() {
    BytesValue idNonce =
        BytesValue.fromHexString(
            "68b02a985ecb99cc2d10cf188879d93ae7684c4f4707770017b078c6497c5a5d");
    Functions.HKDFKeys keys1 =
        Functions.hkdf_expand(
            nodeId1,
            nodeId2,
            testKey1,
            BytesValue.wrap(ECKeyPair.create(testKey2.extractArray()).getPublicKey().toByteArray()),
            idNonce);
    Functions.HKDFKeys keys2 =
        Functions.hkdf_expand(
            nodeId1,
            nodeId2,
            testKey2,
            BytesValue.wrap(ECKeyPair.create(testKey1.extractArray()).getPublicKey().toByteArray()),
            idNonce);
    assertEquals(keys1, keys2);
  }

  @Test
  public void testGcmSimple() {
    BytesValue authResponseKey = BytesValue.fromHexString("0x60bfc5c924a8d640f47df8b781f5a0e5");
    BytesValue authResponsePt =
        BytesValue.fromHexString(
            "0xf8aa05b8404f5fa8309cab170dbeb049de504b519288777aae0c4b25686f82310206a4a1e264dc6e8bfaca9187e8b3dbb56f49c7aa3d22bff3a279bf38fb00cb158b7b8ca7b865f86380018269648276348375647082765f826970847f00000189736563703235366b31b84013d14211e0287b2361a1615890a9b5212080546d0a257ae4cff96cf534992cb97e6adeb003652e807c7f2fe843e0c48d02d4feb0272e2e01f6e27915a431e773");
    BytesValue zeroNonce = BytesValue.wrap(new byte[12]);
    Bytes authResponse =
        Functions.aesgcm_encrypt(authResponseKey, zeroNonce, authResponsePt, BytesValue.EMPTY);
    Bytes authResponsePtDecrypted =
        Functions.aesgcm_decrypt(
            authResponseKey, zeroNonce, BytesValue.wrap(authResponse.toArray()), BytesValue.EMPTY);
    assertEquals(Bytes.wrap(authResponsePt.extractArray()), authResponsePtDecrypted);
  }
}
