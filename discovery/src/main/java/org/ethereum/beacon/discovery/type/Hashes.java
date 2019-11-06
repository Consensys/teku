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

package org.ethereum.beacon.discovery.type;

import java.security.MessageDigest;
import java.security.Security;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.jcajce.provider.digest.SHA256.Digest;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

/** Utility methods to calculate message hashes */
public abstract class Hashes {
  private Hashes() {}

  private static final BouncyCastleProvider PROVIDER;

  private static final String SHA256 = "SHA-256";

  static {
    Security.addProvider(PROVIDER = new BouncyCastleProvider());
  }

  /**
   * A low level method that calculates hash using give algorithm.
   *
   * @param input a message.
   * @param algorithm an algorithm.
   * @return the hash.
   */
  private static byte[] digestUsingAlgorithm(Bytes input, String algorithm) {
    MessageDigest digest;
    try {
      // TODO integrate with JCA without performance loose
      //      digest = MessageDigest.getInstance(algorithm, "BC");
      digest = new Digest();
      input.update(digest);
      return digest.digest();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Calculates sha256 hash.
   *
   * @param input input message.
   * @return the hash.
   */
  public static Bytes sha256(Bytes input) {
    byte[] output = digestUsingAlgorithm(input, SHA256);
    return Bytes.wrap(Bytes.wrap(output));
  }
}
