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

package tech.pegasys.artemis.util.bls;

import net.consensys.cava.bytes.Bytes48;
import tech.pegasys.artemis.util.mikuli.PublicKey;

public class BLSPublicKey {

  /**
   * Generates a compressed, serialised, random, valid public key
   *
   * @return PublicKey The public key, not null
   */
  public static Bytes48 random() {
    return Bytes48.wrap(PublicKey.random().toBytes());
  }

  private PublicKey publicKey;

  BLSPublicKey(PublicKey publicKey) {
    this.publicKey = publicKey;
  }

  PublicKey getPublicKey() {
    return publicKey;
  }
}
