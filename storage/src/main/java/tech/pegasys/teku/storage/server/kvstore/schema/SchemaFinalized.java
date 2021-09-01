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

package tech.pegasys.teku.storage.server.kvstore.schema;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;

public interface SchemaFinalized extends Schema {
  KvStoreColumn<Bytes32, UInt64> getColumnSlotsByFinalizedRoot();

  KvStoreColumn<UInt64, SignedBeaconBlock> getColumnFinalizedBlocksBySlot();

  KvStoreColumn<Bytes32, UInt64> getColumnSlotsByFinalizedStateRoot();

  KvStoreColumn<Bytes32, SignedBeaconBlock> getColumnNonCanonicalBlocksByRoot();

  KvStoreColumn<UInt64, Set<Bytes32>> getColumnNonCanonicalRootsBySlot();

  @Override
  default Collection<KvStoreColumn<?, ?>> getAllColumns() {
    return getColumnMap().values();
  }

  Map<String, KvStoreColumn<?, ?>> getColumnMap();

  @Override
  default Collection<KvStoreVariable<?>> getAllVariables() {
    return Collections.emptyList();
  }

  default Map<String, KvStoreVariable<?>> getVariableMap() {
    return Collections.emptyMap();
  }
}
