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
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.operations.Attestation;

/** ChainStorage Interface class */
public interface ChainStorage {

  static final Logger LOG = LogManager.getLogger();

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
  static <S extends Bytes, T extends HashMap<S, S>> void add(S key, S value, T items) {
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
  static <S extends Bytes, T extends HashMap<S, S>> Optional<S> get(S key, T items) {
    Optional<S> result = Optional.ofNullable(null);
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
      result = Optional.of(items.remove());
    } catch (NoSuchElementException e) {
      LOG.debug(items.getClass().toString() + ": There is nothing to remove");
    }
    return result;
  }

  /**
   * Add processed block to storage
   *
   * @param blockHash
   * @param block
   */
  void addProcessedBlock(Bytes blockHash, BeaconBlock block);

  /**
   * Add unprocessed block to storage
   *
   * @param block
   */
  void addUnprocessedBlock(BeaconBlock block);

  /**
   * Add unprocessed attestation to storage
   *
   * @param attestation
   */
  void addUnprocessedAttestation(Attestation attestation);

  /**
   * Retrieves processed block
   *
   * @param blockHash
   * @return
   */
  Optional<Bytes> getProcessedBlock(Bytes blockHash);

  /**
   * Removes an unprocessed block (LIFO)
   *
   * @return
   */
  Optional<BeaconBlock> getUnprocessedBlock();

  /**
   * Removes an unprocessed attestation (LIFO)
   *
   * @return
   */
  Optional<Attestation> getUnprocessedAttestation();
}
