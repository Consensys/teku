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

package tech.pegasys.artemis.datastructures.state;

import com.google.common.primitives.UnsignedLong;
import java.util.List;
import tech.pegasys.artemis.datastructures.util.cache.Cache;
import tech.pegasys.artemis.datastructures.util.cache.LRUCache;
import tech.pegasys.artemis.datastructures.util.cache.NoOpCache;

public class TransitionCaches {

  public static int MAX_ACTIVE_VALIDATORS_CACHE = 8;

  private static final TransitionCaches NO_OP_INSTANCE =
      new TransitionCaches(NoOpCache.getNoOpCache()) {

        @Override
        public TransitionCaches copy() {
          return this;
        }
      };

  public static TransitionCaches createNewEmpty() {
    return new TransitionCaches();
  }

  public static TransitionCaches getNoOp() {
    return NO_OP_INSTANCE;
  }

  private final Cache<UnsignedLong, List<Integer>> activeValidators;

  private TransitionCaches() {
    activeValidators = new LRUCache<>(MAX_ACTIVE_VALIDATORS_CACHE);
  }

  private TransitionCaches(Cache<UnsignedLong, List<Integer>> activeValidators) {
    this.activeValidators = activeValidators;
  }

  public Cache<UnsignedLong, List<Integer>> getActiveValidators() {
    return activeValidators;
  }

  public TransitionCaches copy() {
    return new TransitionCaches(activeValidators.copy());
  }
}
