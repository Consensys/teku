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

package tech.pegasys.teku.storage.server.rocksdb.schema;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public interface SchemaFinalized extends Schema {
  RocksDbColumn<Bytes32, UInt64> getColumnSlotsByFinalizedRoot();

  RocksDbColumn<UInt64, SignedBeaconBlock> getColumnFinalizedBlocksBySlot();

  RocksDbColumn<UInt64, BeaconState> getColumnFinalizedStatesBySlot();

  RocksDbColumn<Bytes32, UInt64> getColumnSlotsByFinalizedStateRoot();
}
