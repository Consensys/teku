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

package tech.pegasys.artemis.bls.hashToG2;

import static com.google.common.base.Preconditions.checkArgument;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.crypto.Hash;

/** A collection of useful IETF standardised tools. */
public class IetfTools {

  private static final int SHA256_HASH_SIZE = 32;
  private static final int SHA256_BLOCK_SIZE = 64;

  /**
   * Standard HMAC SHA-256 based on https://tools.ietf.org/html/rfc2104
   *
   * @param text the data to be hashed
   * @param key the key
   * @return Bytes of the HMAC SHA-256 of the text with key
   */
  public static Bytes HMAC_SHA256(byte[] text, byte[] key) {

    // SHA256 blocksize in bytes
    int blockSize = SHA256_BLOCK_SIZE;
    byte ipad = (byte) 0x36;
    byte opad = (byte) 0x5c;

    if (key.length > blockSize) {
      key = Hash.sha2_256(key);
    }

    // Pad or truncate the key to the blocksize
    byte[] ikmPadded = new byte[blockSize];
    System.arraycopy(key, 0, ikmPadded, 0, key.length);

    byte[] ikmXorIpad = new byte[blockSize];
    byte[] ikmXorOpad = new byte[blockSize];
    for (int i = 0; i < blockSize; i++) {
      ikmXorIpad[i] = (byte) (ikmPadded[i] ^ ipad);
      ikmXorOpad[i] = (byte) (ikmPadded[i] ^ opad);
    }

    return Hash.sha2_256(
        Bytes.concatenate(
            Bytes.wrap(ikmXorOpad),
            Hash.sha2_256(Bytes.concatenate(Bytes.wrap(ikmXorIpad), Bytes.wrap(text)))));
  }

  /**
   * Standard HKDF_Extract as defined at https://tools.ietf.org/html/rfc5869#section-2.2
   *
   * <p>Note that the arguments to HMAC_SHA-256 appear to be inverted in RFC5869.
   *
   * @param salt salt value (a non-secret random value)
   * @param ikm input keying material
   * @return a pseudorandom key (of HashLen octets)
   */
  public static Bytes HKDF_Extract(Bytes salt, Bytes ikm) {
    return HMAC_SHA256(ikm.toArray(), salt.toArray());
  }

  /**
   * Standard HKDF_Expand as defined at https://tools.ietf.org/html/rfc5869#section-2.3
   *
   * @param prk a pseudorandom key of at least HashLen octets
   * @param info optional context and application specific information
   * @param length desired length of output keying material in octets
   * @return output keying material (of `length` octets)
   */
  public static Bytes HKDF_Expand(Bytes prk, Bytes info, int length) {
    checkArgument(prk.size() >= SHA256_HASH_SIZE, "prk must be larger than the hash length.");
    checkArgument(
        length > 0 && length <= 255 * SHA256_HASH_SIZE,
        "length must be non-zero and not more than " + 255 * SHA256_HASH_SIZE);
    Bytes okm = Bytes.EMPTY;
    Bytes tOld = Bytes.EMPTY;
    int i = 1;
    int remainder = length;
    while (remainder > 0) {
      Bytes tNew =
          HMAC_SHA256(Bytes.concatenate(tOld, info, Bytes.of((byte) i)).toArray(), prk.toArray());
      okm = Bytes.concatenate(okm, tNew);
      i += 1;
      remainder -= SHA256_HASH_SIZE;
      tOld = tNew;
    }
    return okm.slice(0, length);
  }
}
