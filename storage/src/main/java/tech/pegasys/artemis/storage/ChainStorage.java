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

package tech.pegasys.artemis.storage;

import com.google.common.eventbus.EventBus;
import java.util.HashMap;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Queue;
import net.consensys.cava.bytes.Bytes;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** ChainStorage Interface class */
public interface ChainStorage {

  static final Logger LOG = LogManager.getLogger(ChainStorage.class.getName());

  /**
   * Instantiate the ChainStorage
   *
   * @param type
   * @param eventBus
   * @return
   */
  static <T> T Create(Class<T> type, EventBus eventBus) {
    try {
      return type.getDeclaredConstructor(EventBus.class).newInstance(eventBus);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Add item to Queue
   *
   * @param item
   * @param items
   */
  static <S, T extends Queue<S>> void add(S item, T items) {
    try {
      items.add(item);
    } catch (IllegalStateException e) {
      LOG.debug(items.getClass().toString() + ": " + e.getMessage().toString());
    }
  }

  /**
   * Add a value to a HashMap
   *
   * @param key
   * @param value
   * @param items
   */
  static <S extends Bytes, T, U extends HashMap<S, T>> void add(S key, T value, U items) {
    try {
      items.put(key, value);
    } catch (IllegalStateException e) {
      LOG.debug(items.getClass().toString() + ": " + e.getMessage().toString());
    }
  }

  /**
   * Retrieve a value from a HashMap
   *
   * @param key
   * @param items
   * @return
   */
  static <S extends Bytes, T, U extends HashMap<S, T>> Optional<T> get(S key, U items) {
    Optional<T> result = Optional.ofNullable(null);
    try {
      result = Optional.of(items.get(key));
    } catch (NoSuchElementException e) {
      LOG.debug(items.getClass().toString() + ": There is nothing to remove");
    }
    return result;
  }

  /**
   * Remove an item from a Queue
   *
   * @param items
   * @return
   */
  static <S, T extends Queue<S>> Optional<S> remove(T items) {
    Optional<S> result = Optional.ofNullable(null);
    try {
      if (items.size() > 0) {
        result = Optional.of(items.remove());
      }
    } catch (NoSuchElementException e) {
      LOG.debug(items.getClass().toString() + ": There is nothing to remove");
    }
    return result;
  }
}
