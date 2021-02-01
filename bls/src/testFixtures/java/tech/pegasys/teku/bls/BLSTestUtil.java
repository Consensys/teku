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

package tech.pegasys.teku.bls;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.Random;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class BLSTestUtil {

  /**
   * Create a random, but valid, signature.
   *
   * <p>Generally prefer the seeded version.
   *
   * @return a random signature
   */
  public static BLSSignature randomSignature() {
    return randomSignature(new Random().nextInt());
  }

  /**
   * Creates a random, but valid, signature based on a seed.
   *
   * @param entropy to seed the key pair generation
   * @return the signature
   */
  public static BLSSignature randomSignature(int entropy) {
    BLSKeyPair keyPair = randomKeyPair(entropy);
    byte[] message = "Hello, world!".getBytes(UTF_8);
    return BLS.sign(keyPair.getSecretKey(), Bytes.wrap(message));
  }

  /**
   * Generates a compressed, serialized, random, valid public key based on a seed.
   *
   * @return PublicKey The public key, not null
   */
  public static BLSPublicKey randomPublicKey(int seed) {
    return randomKeyPair(seed).getPublicKey();
  }

  /**
   * Generate a key pair based on a secret key generated from a seed value.
   *
   * <p>This MUST NOT be used to generate production keys.
   *
   * @return a keypair generated from a seed
   */
  public static BLSKeyPair randomKeyPair(int seed) {
    BLSSecretKey pseudoRandomSecretBytes =
        BLSSecretKey.fromBytesModR(Bytes32.random(new Random(seed)));
    return new BLSKeyPair(pseudoRandomSecretBytes);
  }
}
