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

package org.ethereum.beacon.ssz;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Hasher Class -> bytes[] */
public interface BytesHasher {
  /**
   * Hashes input
   *
   * @param input Input value
   * @param clazz Class of value
   * @return serialization
   */
  <C> byte[] hash(@Nullable C input, Class<? extends C> clazz);

  /**
   * Hashes truncated input. Prepares virtual object, which gets all fields from input except the
   * last one.
   *
   * @param input Input value
   * @param clazz Class of value
   * @return serialization
   */
  <C> byte[] hashTruncateLast(@Nullable C input, Class<? extends C> clazz);

  /**
   * Shortcut to {@link #hash(Object, Class)}. Resolves class using input object. Not suitable for
   * null values.
   */
  default <C> byte[] hash(@Nonnull C input) {
    return hash(input, input.getClass());
  }
}
