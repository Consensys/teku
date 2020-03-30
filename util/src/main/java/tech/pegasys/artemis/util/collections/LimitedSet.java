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

package tech.pegasys.artemis.util.collections;

import java.util.Collections;
import java.util.Set;

/** Helper that creates a thread-safe set with a maximum capacity. */
public final class LimitedSet {

  private LimitedSet() {}

  /**
   * Creates a limited set with a default initial capacity.
   *
   * @param maxSize The maximum number of elements to keep in the set.
   * @param mode A mode that determines which element is evicted when the set exceeds its max size.
   * @param <T> The type of object in the set.
   * @return A thread-safe set that will evict elements when the max size is exceeded.
   */
  public static final <T> Set<T> create(final int maxSize, final LimitStrategy mode) {
    return create(16, maxSize, mode);
  }

  /**
   * @param initialCapacity The initial size to allocate for the set.
   * @param maxSize The maximum number of elements to keep in the set.
   * @param mode A mode that determines which element is evicted when the set exceeds its max size.
   * @param <T> The type of object held in the set.
   * @return A thread-safe set that will evict elements when the max size is exceeded.
   */
  public static final <T> Set<T> create(
      final int initialCapacity, final int maxSize, final LimitStrategy mode) {
    return Collections.synchronizedSet(
        Collections.newSetFromMap(LimitedMap.create(initialCapacity, maxSize, mode)));
  }
}
