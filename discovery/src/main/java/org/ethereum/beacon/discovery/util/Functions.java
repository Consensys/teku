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

package org.ethereum.beacon.discovery.util;

import static org.ethereum.beacon.discovery.util.CryptoUtil.sha256;
import static org.web3j.crypto.Sign.CURVE_PARAMS;

import com.google.common.base.Objects;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.security.SignatureException;
import java.util.Random;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.MutableBytes;
import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.generators.HKDFBytesGenerator;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.HKDFParameters;
import org.bouncycastle.math.ec.ECPoint;
import org.ethereum.beacon.discovery.type.BytesValue;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Sign;

// import tech.pegasys.artemis.util.bytes.Bytes;
// import tech.pegasys.artemis.util.bytes.Bytess;
// import tech.pegasys.artemis.util.bytes.Bytes;

public class Functions {

  private static final int RECIPIENT_KEY_LENGTH = 16;
  private static final int INITIATOR_KEY_LENGTH = 16;
  private static final int AUTH_RESP_KEY_LENGTH = 16;

  public static Bytes hash(Bytes value) {
    return sha256(value);
  }

  /**
   * Creates a signature of message `x` using the given key
   *
   * @param key private key
   * @param x message
   * @return ECDSA signature with properties merged together: r || s
   */
  public static Bytes sign(Bytes key, Bytes x) {
    Sign.SignatureData signatureData =
        Sign.signMessage(x.toArray(), ECKeyPair.create(key.toArray()));
    Bytes r = Bytes.wrap(signatureData.getR());
    Bytes s = Bytes.wrap(signatureData.getS());
    return Bytes.concatenate(r, s);
  }

  /**
   * Recovers public key from message and signature
   *
   * @param signature Signature, ECDSA
   * @param x message
   * @return public key
   * @throws SignatureException when recovery is not possible
   */
  public static Bytes recoverFromSignature(Bytes signature, Bytes x) throws SignatureException {
    BigInteger publicKey =
        Sign.signedMessageToKey(
            x.toArray(),
            new Sign.SignatureData(
                signature.get(0), signature.slice(1, 33).toArray(), signature.slice(33).toArray()));
    return Bytes.wrap(publicKey.toByteArray());
  }

  /**
   * AES-GCM encryption/authentication with the given `key`, `nonce` and additional authenticated
   * data `ad`. Size of `key` is 16 bytes (AES-128), size of `nonce` 12 bytes.
   */
  public static Bytes aesgcm_encrypt(Bytes privateKey, Bytes nonce, Bytes message, Bytes aad) {
    try {
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(
          Cipher.ENCRYPT_MODE,
          new SecretKeySpec(privateKey.toArray(), "AES"),
          new GCMParameterSpec(128, nonce.toArray()));
      cipher.updateAAD(aad.toArray());
      return Bytes.wrap(cipher.doFinal(message.toArray()));
    } catch (Exception e) {
      throw new RuntimeException("No AES/GCM cipher provider", e);
    }
  }

  public static Bytes aesgcm_encrypt(
      BytesValue privateKey, BytesValue nonce, BytesValue message, BytesValue aad) {
    return aesgcm_encrypt(
        Bytes.wrap(privateKey.extractArray()),
        Bytes.wrap(nonce.extractArray()),
        Bytes.wrap(message.extractArray()),
        Bytes.wrap(aad.extractArray()));
  }

  public static Bytes aesgcm_decrypt(Bytes privateKey, Bytes nonce, Bytes encoded, Bytes aad) {
    try {
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(
          Cipher.DECRYPT_MODE,
          new SecretKeySpec(privateKey.toArray(), "AES"),
          new GCMParameterSpec(128, nonce.toArray()));
      cipher.updateAAD(aad.toArray());
      return Bytes.wrap(cipher.doFinal(encoded.toArray()));
    } catch (Exception e) {
      throw new RuntimeException("No AES/GCM cipher provider", e);
    }
  }

  public static Bytes aesgcm_decrypt(
      BytesValue privateKey, BytesValue nonce, BytesValue encoded, BytesValue aad) {
    return aesgcm_decrypt(
        Bytes.wrap(privateKey.extractArray()),
        Bytes.wrap(nonce.extractArray()),
        Bytes.wrap(encoded.extractArray()),
        Bytes.wrap(aad.extractArray()));
  }

  public static HKDFKeys hkdf_expand(
      BytesValue srcNodeId,
      BytesValue destNodeId,
      BytesValue srcPrivKey,
      BytesValue destPubKey,
      BytesValue idNonce) {
    return hkdf_expand(
        Bytes.wrap(srcNodeId.extractArray()),
        Bytes.wrap(destNodeId.extractArray()),
        Bytes.wrap(srcPrivKey.extractArray()),
        Bytes.wrap(destPubKey.extractArray()),
        Bytes.wrap(idNonce.extractArray()));
  }

  /**
   * The ephemeral key is used to perform Diffie-Hellman key agreement with B's static public key
   * and the session keys are derived from it using the HKDF key derivation function.
   *
   * <p><code>
   * ephemeral-key = random private key ephemeral-pubkey = public key corresponding to ephemeral-key
   * dest-pubkey = public key of B secret = agree(ephemeral-key, dest-pubkey) info = "discovery v5
   * key agreement" || node-id-A || node-id-B prk = HKDF-Extract(secret, id-nonce) initiator-key,
   * recipient-key, auth-resp-key = HKDF-Expand(prk, info)</code>
   */
  public static HKDFKeys hkdf_expand(
      Bytes srcNodeId, Bytes destNodeId, Bytes srcPrivKey, Bytes destPubKey, Bytes idNonce) {
    try {
      ECDomainParameters CURVE =
          new ECDomainParameters(
              CURVE_PARAMS.getCurve(),
              CURVE_PARAMS.getG(),
              CURVE_PARAMS.getN(),
              CURVE_PARAMS.getH());

      byte[] destPubPointBytes = new byte[destPubKey.size() + 1];
      destPubPointBytes[0] = 0x04; // default prefix
      System.arraycopy(destPubKey.toArray(), 0, destPubPointBytes, 1, destPubKey.size());
      ECPoint pudDestPoint = CURVE.getCurve().decodePoint(destPubPointBytes);
      ECPoint mult = pudDestPoint.multiply(new BigInteger(1, srcPrivKey.toArray()));
      byte[] keyAgreement = mult.getEncoded(true);

      Bytes info =
          Bytes.concatenate(
              Bytes.wrap("discovery v5 key agreement".getBytes()), srcNodeId, destNodeId);
      HKDFParameters hkdfParameters =
          new HKDFParameters(keyAgreement, idNonce.toArray(), info.toArray());
      Digest digest = new SHA256Digest();
      HKDFBytesGenerator hkdfBytesGenerator = new HKDFBytesGenerator(digest);
      hkdfBytesGenerator.init(hkdfParameters);
      // initiator-key || recipient-key || auth-resp-key
      byte[] hkdfOutputBytes =
          new byte[INITIATOR_KEY_LENGTH + RECIPIENT_KEY_LENGTH + AUTH_RESP_KEY_LENGTH];
      hkdfBytesGenerator.generateBytes(
          hkdfOutputBytes, 0, INITIATOR_KEY_LENGTH + RECIPIENT_KEY_LENGTH + AUTH_RESP_KEY_LENGTH);
      Bytes hkdfOutput = Bytes.wrap(hkdfOutputBytes);
      Bytes initiatorKey = hkdfOutput.slice(0, INITIATOR_KEY_LENGTH);
      Bytes recipientKey = hkdfOutput.slice(INITIATOR_KEY_LENGTH, RECIPIENT_KEY_LENGTH);
      Bytes authRespKey = hkdfOutput.slice(INITIATOR_KEY_LENGTH + RECIPIENT_KEY_LENGTH);
      return new HKDFKeys(initiatorKey, recipientKey, authRespKey);
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  public static Random getRandom() {
    return new SecureRandom();
  }

  /**
   * The 'distance' between two node IDs is the bitwise XOR of the IDs, taken as the number.
   *
   * <p>distance(n₁, n₂) = n₁ XOR n₂
   *
   * <p>LogDistance is reverse of length of common prefix in bits (length - number of leftmost zeros
   * in XOR)
   */
  public static int logDistance(Bytes nodeId1, Bytes nodeId2) {
    Bytes distance = nodeId1.xor(nodeId2, MutableBytes.create(nodeId2.size()));
    int logDistance = Byte.SIZE * distance.size(); // 256
    final int maxLogDistance = logDistance;
    for (int i = 0; i < maxLogDistance; ++i) {
      if (BytesValue.wrap(distance.toArray()).getHighBit(i)) {
        break;
      } else {
        logDistance--;
      }
    }
    return logDistance;
  }

  public static int logDistance(BytesValue nodeId1, BytesValue nodeId2) {
    return logDistance(Bytes.wrap(nodeId1.extractArray()), Bytes.wrap(nodeId2.extractArray()));
  }

  public static class HKDFKeys {

    private final Bytes initiatorKey;
    private final Bytes recipientKey;
    private final Bytes authResponseKey;

    public HKDFKeys(Bytes initiatorKey, Bytes recipientKey, Bytes authResponseKey) {
      this.initiatorKey = initiatorKey;
      this.recipientKey = recipientKey;
      this.authResponseKey = authResponseKey;
    }

    public Bytes getInitiatorKey() {
      return initiatorKey;
    }

    public Bytes getRecipientKey() {
      return recipientKey;
    }

    public Bytes getAuthResponseKey() {
      return authResponseKey;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      HKDFKeys hkdfKeys = (HKDFKeys) o;
      return Objects.equal(initiatorKey, hkdfKeys.initiatorKey)
          && Objects.equal(recipientKey, hkdfKeys.recipientKey)
          && Objects.equal(authResponseKey, hkdfKeys.authResponseKey);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(initiatorKey, recipientKey, authResponseKey);
    }
  }
}
