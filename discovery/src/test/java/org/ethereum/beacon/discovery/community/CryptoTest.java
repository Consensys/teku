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

package org.ethereum.beacon.discovery.community;

import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.packet.AuthHeaderMessagePacket;
import org.ethereum.beacon.discovery.util.Functions;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** Tests crypto functions */
public class CryptoTest {

  /**
   * The ECDH function takes the elliptic-curve scalar multiplication of a public key and a private
   * key. The wire protocol describes this process.
   *
   * <p>The input public key is an uncompressed secp256k1 key (64 bytes) and the private key is a
   * raw secp256k1 private key (32 bytes).
   */
  @Test
  public void testECDHFunction() {
    Bytes publicKey =
        Bytes.fromHexString(
            "0x9961e4c2356d61bedb83052c115d311acb3a96f5777296dcf297351130266231503061ac4aaee666073d7e5bc2c80c3f5c5b500c1cb5fd0a76abbb6b675ad157");
    Bytes secretKey =
        Bytes.fromHexString("0xfb757dc581730490a1d7a00deea65e9b1936924caaea8f44d476014856b68736");

    Bytes expectedSharedSecret =
        Bytes.fromHexString("0x033b11a2a1f214567e1537ce5e509ffd9b21373247f2a3ff6841f4976f53165e7e");
    Assertions.assertEquals(
        expectedSharedSecret, Functions.deriveECDHKeyAgreement(secretKey, publicKey));
  }

  /**
   * This test vector takes a secret key (as calculated from the previous test vector) along with
   * two node id's and an `id-nonce`. This demonstrates the HKDF-EXPAND and HKDF-EXTRACT functions
   * using the added key-agreement string as described in the wire specification.
   *
   * <p>Given a secret key (calculated from ECDH above) two `node-id`s (required to build the `info`
   * as described in the specification) and the `id-nonce` (required for the HKDF-EXTRACT function),
   * this should produce an `initiator-key`, `recipient-key` and an `auth-resp-key`.
   */
  @Test
  public void testHKDFExpand() {
    Bytes secretKey =
        Bytes.fromHexString("0x02a77e3aa0c144ae7c0a3af73692b7d6e5b7a2fdc0eda16e8d5e6cb0d08e88dd04");
    Bytes nodeIdA =
        Bytes.fromHexString("0xa448f24c6d18e575453db13171562b71999873db5b286df957af199ec94617f7");
    Bytes nodeIdB =
        Bytes.fromHexString("0x885bba8dfeddd49855459df852ad5b63d13a3fae593f3f9fa7e317fd43651409");
    Bytes idNonce =
        Bytes.fromHexString("0x0101010101010101010101010101010101010101010101010101010101010101");

    Bytes expectedInitiatorKey = Bytes.fromHexString("0x238d8b50e4363cf603a48c6cc3542967");
    Bytes expectedRecipientKey = Bytes.fromHexString("0xbebc0183484f7e7ca2ac32e3d72c8891");
    Bytes expectedAuthResponseKey = Bytes.fromHexString("0xe987ad9e414d5b4f9bfe4ff1e52f2fae");
    Functions.HKDFKeys keys = Functions.hkdf_expand(nodeIdA, nodeIdB, secretKey, idNonce);
    Assertions.assertEquals(expectedInitiatorKey, keys.getInitiatorKey());
    Assertions.assertEquals(expectedRecipientKey, keys.getRecipientKey());
    Assertions.assertEquals(expectedAuthResponseKey, keys.getAuthResponseKey());
  }

  /**
   * Nonce signatures should prefix the string `discovery-id-nonce` and post-fix the ephemeral key
   * before taking the `sha256` hash of the `id-nonce`.
   *
   * <p>See {@link org.ethereum.beacon.discovery.packet.AuthHeaderMessagePacket}, idNonceSig is a
   * part of this packet
   */
  @Test
  public void testIdNonceSigning() {
    Bytes idNonce =
        Bytes.fromHexString("0xa77e3aa0c144ae7c0a3af73692b7d6e5b7a2fdc0eda16e8d5e6cb0d08e88dd04");
    Bytes ephemeralKey =
        Bytes.fromHexString(
            "0x9961e4c2356d61bedb83052c115d311acb3a96f5777296dcf297351130266231503061ac4aaee666073d7e5bc2c80c3f5c5b500c1cb5fd0a76abbb6b675ad157");
    Bytes localSecretKey =
        Bytes.fromHexString("0xfb757dc581730490a1d7a00deea65e9b1936924caaea8f44d476014856b68736");

    Bytes expectedIdNonceSig =
        Bytes.fromHexString(
            "0xc5036e702a79902ad8aa147dabfe3958b523fd6fa36cc78e2889b912d682d8d35fdea142e141f690736d86f50b39746ba2d2fc510b46f82ee08f08fd55d133a4");
    Assertions.assertEquals(
        expectedIdNonceSig,
        AuthHeaderMessagePacket.signIdNonce(idNonce, localSecretKey, ephemeralKey));
  }

  /**
   * This test vector demonstrates the `AES_GCM` encryption/decryption used in the wire protocol.
   */
  @Test
  public void testAESGCM() {
    Bytes encryptionKey = Bytes.fromHexString("0x9f2d77db7004bf8a1a85107ac686990b");
    Bytes nonce = Bytes.fromHexString("0x27b5af763c446acd2749fe8e");
    Bytes pt = Bytes.fromHexString("0x01c20101");
    Bytes ad =
        Bytes.fromHexString("0x93a7400fa0d6a694ebc24d5cf570f65d04215b6ac00757875e3f3a5f42107903");

    Bytes expectedMessageCiphertext =
        Bytes.fromHexString("a5d12a2d94b8ccb3ba55558229867dc13bfa3648");
    Assertions.assertEquals(
        expectedMessageCiphertext, Functions.aesgcm_encrypt(encryptionKey, nonce, pt, ad));
  }
}
