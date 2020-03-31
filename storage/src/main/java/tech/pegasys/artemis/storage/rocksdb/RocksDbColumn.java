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

package tech.pegasys.artemis.storage.rocksdb;

import java.nio.charset.StandardCharsets;

public enum RocksDbColumn {
  DEFAULT("default".getBytes(StandardCharsets.UTF_8), "default"),
  FINALIZED_ROOTS_BY_SLOT((byte) 1, "finalizedRootsBySlot"),
  FINALIZED_BLOCKS_BY_ROOT((byte) 2, "finalizedBlocksByRoot"),
  FINALIZED_STATES_BY_ROOT((byte) 3, "finalizedStatesByRoot"),
  HOT_BLOCKS_BY_ROOT((byte) 4, "hotBlocksByRoot"),
  HOT_STATES_BY_ROOT((byte) 5, "hotStatesByRoot"),
  CHECKPOINT_STATES((byte) 6, "checkpointStates"),
  LATEST_MESSAGES((byte) 7, "latestMessages");

  private final byte[] id;
  private final String name;

  RocksDbColumn(final byte id, final String name) {
    this(new byte[] {id}, name);
  }

  RocksDbColumn(final byte[] id, final String name) {
    this.id = id;
    this.name = name;
  }

  public byte[] getId() {
    return id;
  }

  public String getName() {
    return name;
  }
}
