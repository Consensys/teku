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

package org.ethereum.beacon.core.types;

import java.util.Optional;
import java.util.function.Function;
import tech.pegasys.artemis.ethereum.core.Hash;

/**
 * Indicates hashable type, with hash of type T
 *
 * @param <T> hash type, descendant of {@link Hash}
 */
public interface Hashable<T extends Hash> {
  /** Hash of the object */
  Optional<T> getHash();

  /** Sets hash for object, the only way to set it, but object itself could reset it at any time */
  void setHash(T hash);

  default T getHash(Function<Object, T> hasher) {
    Optional<T> cachedHash = getHash();
    if (!cachedHash.isPresent()) {
      T hash = hasher.apply(this);
      setHash(hash);
      return hash;
    } else {
      return cachedHash.get();
    }
  }
}
