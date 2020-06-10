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

package tech.pegasys.teku.storage;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** ChainStorage Interface class */
public interface ChainStorage {
  Logger LOG = LogManager.getLogger();

  /**
   * Retrieve a value from a HashMap
   *
   * @param key
   * @param items
   * @return
   */
  static <S, T, U extends ConcurrentHashMap<S, T>> Optional<T> get(S key, U items) {
    Optional<T> result = Optional.empty();
    try {
      if (items.size() > 0) {
        result = Optional.of(items.get(key));
      }
    } catch (NullPointerException e) {
      if (!key.toString()
          .equalsIgnoreCase("0x0000000000000000000000000000000000000000000000000000000000000000")) {
        LOG.debug("{}: {} not found", items.getClass().toString(), key.toString());
      }
    }
    return result;
  }
}
