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

package tech.pegasys.artemis.storage.server.rocksdb;

public enum RocksDbDefaultColumnKey {
  GENESIS_TIME_KEY((byte) 1, "genesisTimeKey"),
  JUSTIFIED_CHECKPOINT_KEY((byte) 2, "justifiedCheckpointKey"),
  BEST_JUSTIFIED_CHECKPOINT_KEY((byte) 3, "bestJustifiedCheckpointKey"),
  FINALIZED_CHECKPOINT_KEY((byte) 4, "finalizedCheckpointKey");

  private final byte[] id;
  private final String name;

  RocksDbDefaultColumnKey(final byte id, final String name) {
    this.id = new byte[] {id};
    this.name = name;
  }

  public byte[] getId() {
    return id;
  }

  public String getName() {
    return name;
  }
}
