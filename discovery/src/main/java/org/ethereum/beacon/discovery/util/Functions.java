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

import static org.ethereum.beacon.discovery.util.Utils.extractBytesFromUnsignedBigInt;
import static org.web3j.crypto.Sign.CURVE_PARAMS;

import com.google.common.base.Objects;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Random;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.generators.HKDFBytesGenerator;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.HKDFParameters;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.util.Arrays;
import org.ethereum.beacon.discovery.type.Hashes;
import org.web3j.crypto.ECDSASignature;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Hash;
import org.web3j.crypto.Sign;

/** Set of cryptography and utilities functions used in discovery */
public class Functions {
  public static final ECDomainParameters SECP256K1_CURVE =
      new ECDomainParameters(
          CURVE_PARAMS.getCurve(), CURVE_PARAMS.getG(), CURVE_PARAMS.getN(), CURVE_PARAMS.getH());
  public static final int PUBKEY_SIZE = 64;
  private static final int RECIPIENT_KEY_LENGTH = 16;
  private static final int INITIATOR_KEY_LENGTH = 16;
  private static final int AUTH_RESP_KEY_LENGTH = 16;
  private static final int MS_IN_SECOND = 1000;

  /** SHA2 (SHA256) */
  public static Bytes hash(Bytes value) {
    return Hashes.sha256(value);
  }

  /** SHA3 (Keccak256) */
  public static Bytes hashKeccak(Bytes value) {
    return Bytes.wrap(Hash.sha3(value.toArray()));
  }

  /**
   * Creates a signature of message `x` using the given key.
   *
   * @param key private key
   * @param x message, hashed
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
   * Verifies that signature is made by signer
   *
   * @param signature Signature, ECDSA
   * @param x message, hashed
   * @param pubKey Public key of supposed signer, compressed, 33 bytes
   * @return whether `signature` reflects message `x` signed with `pubkey`
   */
  public static boolean verifyECDSASignature(Bytes signature, Bytes x, Bytes pubKey) {
    assert pubKey.size() == 33;
    ECPoint ecPoint = Functions.publicKeyToPoint(pubKey);
    Bytes pubKeyUncompressed = Bytes.wrap(ecPoint.getEncoded(false)).slice(1);
    ECDSASignature ecdsaSignature =
        new ECDSASignature(
            new BigInteger(1, signature.slice(0, 32).toArray()),
            new BigInteger(1, signature.slice(32).toArray()));
    for (int recId = 0; recId < 4; ++recId) {
      BigInteger calculatedPubKey = Sign.recoverFromSignature(recId, ecdsaSignature, x.toArray());
      if (calculatedPubKey == null) {
        continue;
      }
      if (Arrays.areEqual(
          pubKeyUncompressed.toArray(),
          extractBytesFromUnsignedBigInt(calculatedPubKey, PUBKEY_SIZE))) {
        return true;
      }
    }
    return false;
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

  /**
   * AES-GCM decryption of `encoded` data with the given `key`, `nonce` and additional authenticated
   * data `ad`. Size of `key` is 16 bytes (AES-128), size of `nonce` 12 bytes.
   */
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

  /** Maps public key to point on {@link #SECP256K1_CURVE} */
  public static ECPoint publicKeyToPoint(Bytes pkey) {
    //    byte[] destPubPointBytes;
    //    if (pkey.size() == 64) { // uncompressed
    //      destPubPointBytes = new byte[pkey.size() + 1];
    //      destPubPointBytes[0] = 0x04; // default prefix
    //      System.arraycopy(pkey.toArray(), 0, destPubPointBytes, 1, pkey.size());
    //    } else {
    //      destPubPointBytes = pkey.toArray();
    //    }
    ////    return SECP256K1_CURVE.getCurve().decodePoint(destPubPointBytes);
    //    ECP ecp = ECP.fromBytes(pkey.toArray());
    //    byte[] decodePointBytes = new byte[33];
    //    ecp.toBytes(decodePointBytes, true);
    return SECP256K1_CURVE.getCurve().decodePoint(pkey.toArray());
  }

  /** Derives public key in SECP256K1, compressed */
  public static Bytes derivePublicKeyFromPrivate(Bytes privateKey) {
    ECKeyPair ecKeyPair = ECKeyPair.create(privateKey.toArray());
    final Bytes pubKey =
        Bytes.wrap(extractBytesFromUnsignedBigInt(ecKeyPair.getPublicKey(), PUBKEY_SIZE));
    ECPoint ecPoint = Functions.publicKeyToPoint(pubKey);
    return Bytes.wrap(ecPoint.getEncoded(true));
  }

  /** Derives key agreement ECDH by multiplying private key by public */
  public static Bytes deriveECDHKeyAgreement(Bytes srcPrivKey, Bytes destPubKey) {
    ECPoint pudDestPoint = publicKeyToPoint(destPubKey);
    ECPoint mult = pudDestPoint.multiply(new BigInteger(1, srcPrivKey.toArray()));
    return Bytes.wrap(mult.getEncoded(true));
  }

  /**
   * The ephemeral key is used to perform Diffie-Hellman key agreement with B's static public key
   * and the session keys are derived from it using the HKDF key derivation function.
   *
   * <p><code>
   * ephemeral-key = random private key
   * ephemeral-pubkey = public key corresponding to ephemeral-key
   * dest-pubkey = public key of B
   * secret = agree(ephemeral-key, dest-pubkey)
   * info = "discovery v5 key agreement" || node-id-A || node-id-B
   * prk = HKDF-Extract(secret, id-nonce)
   * initiator-key, recipient-key, auth-resp-key = HKDF-Expand(prk, info)</code>
   */
  public static HKDFKeys hkdf_expand(
      Bytes srcNodeId, Bytes destNodeId, Bytes srcPrivKey, Bytes destPubKey, Bytes idNonce) {
    Bytes keyAgreement = deriveECDHKeyAgreement(srcPrivKey, destPubKey);
    return hkdf_expand(srcNodeId, destNodeId, keyAgreement, idNonce);
  }

  /**
   * {@link #hkdf_expand(Bytes, Bytes, Bytes, Bytes, Bytes)} but with keyAgreement already derived
   * by {@link #deriveECDHKeyAgreement(Bytes, Bytes)}
   */
  public static HKDFKeys hkdf_expand(
      Bytes srcNodeId, Bytes destNodeId, Bytes keyAgreement, Bytes idNonce) {
    try {
      Bytes info =
          Bytes.concatenate(
              Bytes.wrap("discovery v5 key agreement".getBytes()), srcNodeId, destNodeId);
      HKDFParameters hkdfParameters =
          new HKDFParameters(keyAgreement.toArray(), idNonce.toArray(), info.toArray());
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

  /** Current time in seconds */
  public static long getTime() {
    return System.currentTimeMillis() / MS_IN_SECOND;
  }

  /** Random provider */
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
    Bytes distance = nodeId1.xor(nodeId2);
    int logDistance = Byte.SIZE * distance.size(); // 256
    final int maxLogDistance = logDistance;
    for (int i = 0; i < maxLogDistance; ++i) {
      boolean highBit = ((distance.get(i / 8) >> (7 - (i % 8))) & 1) == 1;
      if (highBit) {
        break;
      } else {
        logDistance--;
      }
    }
    return logDistance;
  }

  /**
   * Stores set of keys derived by simple key derivation function (KDF) based on a hash-based
   * message authentication code (HMAC)
   */
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
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
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
