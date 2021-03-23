/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.bls.impl;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes;

public interface Signature {

  /**
   * Signature serialization to compressed form
   *
   * @return byte array of size 96
   */
  Bytes toBytesCompressed();

  /**
   * Verify that this aggregated signature is correct for the given pairs of public keys and
   * messages.
   *
   * @param keysToMessages the list of public key and message pairs
   * @return True if the verification is successful, false otherwise
   */
  boolean verify(List<PublicKeyMessagePair> keysToMessages);

  /**
   * Verifies this aggregate signature against a message using the list of public keys.
   *
   * @param publicKeys The list of public keys, not null
   * @param message The message data to verify, not null
   * @return True if the verification is successful, false otherwise
   */
  default boolean verify(List<PublicKey> publicKeys, Bytes message) {
    return verify(
        publicKeys.stream()
            .map(pk -> new PublicKeyMessagePair(pk, message))
            .collect(Collectors.toList()));
  }

  /**
   * Verify that this signature is correct for the given public key and message.
   *
   * @param publicKey The public key, not null
   * @param message the message data to verify, not null
   * @return True if the verification is successful, false otherwise
   */
  default boolean verify(PublicKey publicKey, Bytes message) {
    return verify(Collections.singletonList(publicKey), message);
  }

  /**
   * Verify that this signature is correct for the given public key, message and DST.
   *
   * @param publicKey The public key, not null
   * @param message the message data to verify, not null
   * @param dst domain separation tag (DST), not null
   * @return True if the verification is successful, false otherwise
   */
  boolean verify(PublicKey publicKey, Bytes message, Bytes dst);

  /**
   * Determine if this Signature is the `G2_POINT_AT_INFINITY`.
   *
   * @return true if this signature is the point at infinity, otherwise false.
   */
  boolean isInfinity();

  /** Implementation must override */
  @Override
  int hashCode();

  /** Implementation must override */
  @Override
  boolean equals(Object obj);
}
