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

/** Tests {@link AuthHeaderMessagePacket} packet creation routines */
public class AuthHeaderMessagePacketTest {
  /**
   * This first section entails signature generation, and adding any ENR into `auth-pt`. In this
   * example, there is no ENR sent. This tests the signature generation and correct RLP encoding of
   * the `auth-pt` before encryption.
   */
  @Test
  public void testAuthPtGeneration() {
    Bytes secretKey =
        Bytes.fromHexString("0x7e8107fe766b6d357205280acf65c24275129ca9e44c0fd00144ca50024a1ce7");
    Bytes idNonce =
        Bytes.fromHexString("0xe551b1c44264ab92bc0b3c9b26293e1ba4fed9128f3c3645301e8e119f179c65");
    Bytes ephemeralPubkey =
        Bytes.fromHexString(
            "0xb35608c01ee67edff2cffa424b219940a81cf2fb9b66068b1cf96862a17d353e22524fbdcdebc609f85cbd58ebe7a872b01e24a3829b97dd5875e8ffbc4eea81");
    // enr: []
    Bytes expectedAuthPt =
        Bytes.fromHexString(
            "0xf84405b840f753ac31b017536bacd0d0238a1f849e741aef03b7ad5db1d4e64d7aa80689931f21e590edcf80ee32bb2f30707fec88fb62ea8fbcd65b9272e9a0175fea976bc0");
    Assertions.assertEquals(
        expectedAuthPt,
        Bytes.wrap(
            AuthHeaderMessagePacket.createAuthMessagePt(
                AuthHeaderMessagePacket.signIdNonce(idNonce, secretKey, ephemeralPubkey), null)));
  }

  /**
   * The `auth-pt` must then be encrypted with AES-GCM. The auth-header uses a 12-byte 0 nonce with
   * no authenticated data.
   */
  @Test
  public void testEncryptAuthMessagePt() {
    Bytes authRespKey = Bytes.fromHexString("0x8c7caa563cebc5c06bb15fc1a2d426c3");
    Bytes authPt =
        Bytes.fromHexString(
            "0xf84405b840f753ac31b017536bacd0d0238a1f849e741aef03b7ad5db1d4e64d7aa80689931f21e590edcf80ee32bb2f30707fec88fb62ea8fbcd65b9272e9a0175fea976bc0");

    Bytes expectedAuthRespCiphertext =
        Bytes.fromHexString(
            "0x570fbf23885c674867ab00320294a41732891457969a0f14d11c995668858b2ad731aa7836888020e2ccc6e0e5776d0d4bc4439161798565a4159aa8620992fb51dcb275c4f755c8b8030c82918898f1ac387f606852");
    Assertions.assertEquals(
        expectedAuthRespCiphertext,
        AuthHeaderMessagePacket.encodeAuthResponse(authPt.toArray(), authRespKey));
  }

  /**
   * An authentication header is built. This test vector demonstrates the correct RLP-encoding of
   * the authentication header with the above inputs.
   */
  @Test
  public void testAuthHeaderGeneration() {
    Bytes authTag = Bytes.fromHexString("0x27b5af763c446acd2749fe8e");
    Bytes idNonce =
        Bytes.fromHexString("0xe551b1c44264ab92bc0b3c9b26293e1ba4fed9128f3c3645301e8e119f179c65");
    Bytes ephemeralPubkey =
        Bytes.fromHexString(
            "0xb35608c01ee67edff2cffa424b219940a81cf2fb9b66068b1cf96862a17d353e22524fbdcdebc609f85cbd58ebe7a872b01e24a3829b97dd5875e8ffbc4eea81");
    Bytes authRespCiphertext =
        Bytes.fromHexString(
            "0x570fbf23885c674867ab00320294a41732891457969a0f14d11c995668858b2ad731aa7836888020e2ccc6e0e5776d0d4bc4439161798565a4159aa8620992fb51dcb275c4f755c8b8030c82918898f1ac387f606852");

    Bytes expectedAuthHeaderRlp =
        Bytes.fromHexString(
            "0xf8cc8c27b5af763c446acd2749fe8ea0e551b1c44264ab92bc0b3c9b26293e1ba4fed9128f3c3645301e8e119f179c658367636db840b35608c01ee67edff2cffa424b219940a81cf2fb9b66068b1cf96862a17d353e22524fbdcdebc609f85cbd58ebe7a872b01e24a3829b97dd5875e8ffbc4eea81b856570fbf23885c674867ab00320294a41732891457969a0f14d11c995668858b2ad731aa7836888020e2ccc6e0e5776d0d4bc4439161798565a4159aa8620992fb51dcb275c4f755c8b8030c82918898f1ac387f606852");
    Assertions.assertEquals(
        expectedAuthHeaderRlp,
        AuthHeaderMessagePacket.encodeAuthHeaderRlp(
            authTag, idNonce, ephemeralPubkey, authRespCiphertext));
  }

  /**
   * This combines the previously generated authentication header with encryption of the protocol
   * message, providing the final rlp-encoded message with an authentication header.
   */
  @Test
  public void testEncodeMessage() {
    Bytes tag =
        Bytes.fromHexString("0x93a7400fa0d6a694ebc24d5cf570f65d04215b6ac00757875e3f3a5f42107903");
    Bytes authHeaderRlp =
        Bytes.fromHexString(
            "0xf8cc8c27b5af763c446acd2749fe8ea0e551b1c44264ab92bc0b3c9b26293e1ba4fed9128f3c3645301e8e119f179c658367636db840b35608c01ee67edff2cffa424b219940a81cf2fb9b66068b1cf96862a17d353e22524fbdcdebc609f85cbd58ebe7a872b01e24a3829b97dd5875e8ffbc4eea81b856570fbf23885c674867ab00320294a41732891457969a0f14d11c995668858b2ad731aa7836888020e2ccc6e0e5776d0d4bc4439161798565a4159aa8620992fb51dcb275c4f755c8b8030c82918898f1ac387f606852");
    Bytes encryptionKey = Bytes.fromHexString("0x9f2d77db7004bf8a1a85107ac686990b");
    Bytes messagePlaintext = Bytes.fromHexString("0x01c20101");
    Bytes authTag = Bytes.fromHexString("0x27b5af763c446acd2749fe8e");

    Bytes expectedAuthMessageRlp =
        Bytes.fromHexString(
            "0x93a7400fa0d6a694ebc24d5cf570f65d04215b6ac00757875e3f3a5f42107903f8cc8c27b5af763c446acd2749fe8ea0e551b1c44264ab92bc0b3c9b26293e1ba4fed9128f3c3645301e8e119f179c658367636db840b35608c01ee67edff2cffa424b219940a81cf2fb9b66068b1cf96862a17d353e22524fbdcdebc609f85cbd58ebe7a872b01e24a3829b97dd5875e8ffbc4eea81b856570fbf23885c674867ab00320294a41732891457969a0f14d11c995668858b2ad731aa7836888020e2ccc6e0e5776d0d4bc4439161798565a4159aa8620992fb51dcb275c4f755c8b8030c82918898f1ac387f606852a5d12a2d94b8ccb3ba55558229867dc13bfa3648");
    Bytes encryptedData = Functions.aesgcm_encrypt(encryptionKey, authTag, messagePlaintext, tag);
    Bytes authHeaderMessagePacket = Bytes.concatenate(tag, authHeaderRlp, encryptedData);
    Assertions.assertEquals(expectedAuthMessageRlp, authHeaderMessagePacket);
  }
}
