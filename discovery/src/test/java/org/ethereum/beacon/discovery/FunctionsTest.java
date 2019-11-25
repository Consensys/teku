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

import static org.ethereum.beacon.discovery.packet.AuthHeaderMessagePacket.createIdNonceMessage;
import static org.ethereum.beacon.discovery.util.Functions.PUBKEY_SIZE;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.packet.AuthHeaderMessagePacket;
import org.ethereum.beacon.discovery.util.Functions;
import org.ethereum.beacon.discovery.util.Utils;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.ECKeyPair;

public class FunctionsTest {
  private final Bytes testKey1 =
      Bytes.fromHexString("3332ca2b7003810449b6e596c3d284e914a1a51c9f76e4d9d7d43ef84adf6ed6");
  private final Bytes testKey2 =
      Bytes.fromHexString("66fb62bfbd66b9177a138c1e5cddbe4f7c30c343e94e68df8769459cb1cde628");
  private Bytes nodeId1;
  private Bytes nodeId2;

  public FunctionsTest() {
    byte[] homeNodeIdBytes = new byte[32];
    homeNodeIdBytes[0] = 0x01;
    byte[] destNodeIdBytes = new byte[32];
    destNodeIdBytes[0] = 0x02;
    this.nodeId1 = Bytes.wrap(homeNodeIdBytes);
    this.nodeId2 = Bytes.wrap(destNodeIdBytes);
  }

  @Test
  public void testLogDistance() {
    Bytes nodeId0 =
        Bytes.fromHexString("0000000000000000000000000000000000000000000000000000000000000000");
    Bytes nodeId1a =
        Bytes.fromHexString("0000000000000000000000000000000000000000000000000000000000000001");
    Bytes nodeId1b =
        Bytes.fromHexString("1000000000000000000000000000000000000000000000000000000000000000");
    Bytes nodeId1s =
        Bytes.fromHexString("1111111111111111111111111111111111111111111111111111111111111111");
    Bytes nodeId9s =
        Bytes.fromHexString("9999999999999999999999999999999999999999999999999999999999999999");
    Bytes nodeIdfs =
        Bytes.fromHexString("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff");
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
    Bytes idNonce =
        Bytes.fromHexString("68b02a985ecb99cc2d10cf188879d93ae7684c4f4707770017b078c6497c5a5d");
    Functions.HKDFKeys keys1 =
        Functions.hkdf_expand(
            nodeId1,
            nodeId2,
            testKey1,
            Bytes.wrap(ECKeyPair.create(testKey2.toArray()).getPublicKey().toByteArray()),
            idNonce);
    Functions.HKDFKeys keys2 =
        Functions.hkdf_expand(
            nodeId1,
            nodeId2,
            testKey2,
            Bytes.wrap(ECKeyPair.create(testKey1.toArray()).getPublicKey().toByteArray()),
            idNonce);
    assertEquals(keys1, keys2);
  }

  @Test
  public void testGcmSimple() {
    Bytes authResponseKey = Bytes.fromHexString("0x60bfc5c924a8d640f47df8b781f5a0e5");
    Bytes authResponsePt =
        Bytes.fromHexString(
            "0xf8aa05b8404f5fa8309cab170dbeb049de504b519288777aae0c4b25686f82310206a4a1e264dc6e8bfaca9187e8b3dbb56f49c7aa3d22bff3a279bf38fb00cb158b7b8ca7b865f86380018269648276348375647082765f826970847f00000189736563703235366b31b84013d14211e0287b2361a1615890a9b5212080546d0a257ae4cff96cf534992cb97e6adeb003652e807c7f2fe843e0c48d02d4feb0272e2e01f6e27915a431e773");
    Bytes zeroNonce = Bytes.wrap(new byte[12]);
    Bytes authResponse =
        Functions.aesgcm_encrypt(authResponseKey, zeroNonce, authResponsePt, Bytes.EMPTY);
    Bytes authResponsePtDecrypted =
        Functions.aesgcm_decrypt(authResponseKey, zeroNonce, authResponse, Bytes.EMPTY);
    assertEquals(authResponsePt, authResponsePtDecrypted);
  }

  @Test
  @SuppressWarnings({"DefaultCharset"})
  public void testRecoverFromSignature() throws Exception {
    Bytes idNonceSig =
        Bytes.fromHexString(
            "0xcf2bf743fc2273709bbc5117fd72775b0661ce1b6e9dffa01f45e2307fb138b90da16364ee7ae1705b938f6648d7725d35fe7e3f200e0ea022c1360b9b2e7385");
    Bytes ephemeralKey =
        Bytes.fromHexString(
            "0x9961e4c2356d61bedb83052c115d311acb3a96f5777296dcf297351130266231503061ac4aaee666073d7e5bc2c80c3f5c5b500c1cb5fd0a76abbb6b675ad157");
    Bytes nonce =
        Bytes.fromHexString("0x02a77e3aa0c144ae7c0a3af73692b7d6e5b7a2fdc0eda16e8d5e6cb0d08e88dd04");
    Bytes privKey =
        Bytes.fromHexString("0xfb757dc581730490a1d7a00deea65e9b1936924caaea8f44d476014856b68736");
    Bytes pubKeyUncompressed =
        Bytes.wrap(
            Utils.extractBytesFromUnsignedBigInt(
                ECKeyPair.create(privKey.toArray()).getPublicKey(), PUBKEY_SIZE));
    Bytes pubKey = Bytes.wrap(Functions.publicKeyToPoint(pubKeyUncompressed).getEncoded(true));

    Bytes message =
        Bytes.concatenate(Bytes.wrap("discovery-id-nonce".getBytes()), nonce, ephemeralKey);
    assert Functions.verifyECDSASignature(idNonceSig, Functions.hash(message), pubKey);
  }

  @Test
  public void testSignAndRecoverFromSignature() {
    Bytes idNonce =
        Bytes.fromHexString("0xd550ca9d62930c947efff75b58a4ea1b44716d841cc0d690879d4f3cab5a4e84");
    Bytes ephemeralPubkey =
        Bytes.fromHexString(
            "0xd9bc9158f0a0c40e75490de66ef44f865588d1c7110b29d0c479db19f7644ddad2d8e948cb933bd767437b173888409d73644a36ae1d068997217357a22d674f");
    Bytes privKey =
        Bytes.fromHexString("0xb5a8efa45da6906663cf7a158cd506da71ae7d732a68220e6644468526bb098e");
    Bytes pubKey =
        Bytes.wrap(
            Functions.publicKeyToPoint(
                    Bytes.wrap(
                        Utils.extractBytesFromUnsignedBigInt(
                            ECKeyPair.create(privKey.toArray()).getPublicKey(), 64)))
                .getEncoded(true));
    Bytes idNonceSig = AuthHeaderMessagePacket.signIdNonce(idNonce, privKey, ephemeralPubkey);
    assert Functions.verifyECDSASignature(
        idNonceSig, Functions.hash(createIdNonceMessage(idNonce, ephemeralPubkey)), pubKey);
  }
}
