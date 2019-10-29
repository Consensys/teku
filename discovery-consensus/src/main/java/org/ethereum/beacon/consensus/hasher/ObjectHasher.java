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

package org.ethereum.beacon.consensus.hasher;

import org.ethereum.beacon.core.spec.SpecConstants;
import org.ethereum.beacon.crypto.Hashes;
import tech.pegasys.artemis.ethereum.core.Hash32;
import tech.pegasys.artemis.util.bytes.BytesValue;

/**
 * An interface of object hasher.
 *
 * @param <H> a hash type.
 * @see SSZObjectHasher
 */
public interface ObjectHasher<H extends BytesValue> {

  static ObjectHasher<Hash32> createSSZOverSHA256(SpecConstants specConstants) {
    return SSZObjectHasher.createIncremental(specConstants, Hashes::sha256);
  }

  /**
   * Calculates hash of given object.
   *
   * @param input an object of any type.
   * @return calculated hash.
   */
  H getHash(Object input);

  /**
   * Calculates hash of the object skipping its last field.
   *
   * @param input an object.
   * @return calculated hash.
   */
  H getHashTruncateLast(Object input);
}
