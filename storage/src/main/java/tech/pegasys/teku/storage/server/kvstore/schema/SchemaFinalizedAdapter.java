/*
 * Copyright 2022 ConsenSys AG.
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

import java.util.Map;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;

public abstract class SchemaFinalizedAdapter implements SchemaFinalized {
  private final SchemaCombined delegate;

  protected SchemaFinalizedAdapter(final SchemaCombined delegate) {
    this.delegate = delegate;
  }

  @Override
  public KvStoreColumn<Bytes32, UInt64> getColumnSlotsByFinalizedRoot() {
    return delegate.getColumnSlotsByFinalizedRoot();
  }

  @Override
  public KvStoreColumn<UInt64, SignedBeaconBlock> getColumnFinalizedBlocksBySlot() {
    return delegate.getColumnFinalizedBlocksBySlot();
  }

  @Override
  public KvStoreColumn<Bytes32, UInt64> getColumnSlotsByFinalizedStateRoot() {
    return delegate.getColumnSlotsByFinalizedStateRoot();
  }

  @Override
  public KvStoreColumn<Bytes32, SignedBeaconBlock> getColumnNonCanonicalBlocksByRoot() {
    return delegate.getColumnNonCanonicalBlocksByRoot();
  }

  @Override
  public KvStoreColumn<UInt64, Set<Bytes32>> getColumnNonCanonicalRootsBySlot() {
    return delegate.getColumnNonCanonicalRootsBySlot();
  }

  @Override
  public KvStoreVariable<UInt64> getOptimisticTransitionBlockSlot() {
    return delegate.getOptimisticTransitionBlockSlot();
  }

  @Override
  public Map<String, KvStoreVariable<?>> getVariableMap() {
    return Map.of("OPTIMISTIC_TRANSITION_BLOCK_SLOT", getOptimisticTransitionBlockSlot());
  }
}
