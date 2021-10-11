/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.storage.server.kvstore.dataaccess;

import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import tech.pegasys.teku.infrastructure.collections.LimitedSet;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.ssz.tree.GIndexUtil;
import tech.pegasys.teku.ssz.tree.TreeNodeStore;
import tech.pegasys.teku.storage.server.kvstore.KvStoreAccessor;
import tech.pegasys.teku.storage.server.kvstore.KvStoreAccessor.KvStoreTransaction;
import tech.pegasys.teku.storage.server.kvstore.schema.SchemaFinalizedTreeState;

public class V4FinalizedStateTreeStorageLogic
    implements V4FinalizedStateStorageLogic<SchemaFinalizedTreeState> {
  private static final int MAX_BRANCH_LEVELS_SKIPPED = 5;
  private final LabelledMetric<Counter> branchNodeStoredCounter;
  private Set<Bytes32> knownStoredBranchesCache;
  private final Spec spec;

  public V4FinalizedStateTreeStorageLogic(
      final MetricsSystem metricsSystem, final Spec spec, final int maxKnownNodeCacheSize) {
    this.spec = spec;
    this.knownStoredBranchesCache = LimitedSet.create(maxKnownNodeCacheSize);
    this.branchNodeStoredCounter =
        metricsSystem.createLabelledCounter(
            TekuMetricCategory.STORAGE_FINALIZED_DB,
            "state_branch_nodes",
            "Number of finalized state tree branch nodes stored vs skipped",
            "type");
  }

  @Override
  public Optional<BeaconState> getLatestAvailableFinalizedState(
      final KvStoreAccessor db, final SchemaFinalizedTreeState dbSchema, final UInt64 maxSlot) {
    return db.getFloorEntry(dbSchema.getColumnFinalizedStateRootsBySlot(), maxSlot)
        .map(
            entry ->
                spec.atSlot(entry.getKey())
                    .getSchemaDefinitions()
                    .getBeaconStateSchema()
                    .load(
                        new KvStoreTreeNodeSource(db, dbSchema),
                        entry.getValue(),
                        GIndexUtil.SELF_G_INDEX));
  }

  @Override
  public FinalizedStateUpdater<SchemaFinalizedTreeState> updater() {
    return new StateTreeUpdater(knownStoredBranchesCache, branchNodeStoredCounter);
  }

  private static class StateTreeUpdater implements FinalizedStateUpdater<SchemaFinalizedTreeState> {

    private final Set<Bytes32> knownStoredBranchesCache;
    private final LabelledMetric<Counter> branchNodeStoredCounter;
    private TreeNodeStore nodeStore;

    private StateTreeUpdater(
        final Set<Bytes32> knownStoredBranchesCache,
        final LabelledMetric<Counter> branchNodeStoredCounter) {
      this.knownStoredBranchesCache = knownStoredBranchesCache;
      this.branchNodeStoredCounter = branchNodeStoredCounter;
    }

    @Override
    public void addFinalizedState(
        final KvStoreAccessor db,
        final KvStoreTransaction transaction,
        final SchemaFinalizedTreeState schema,
        final BeaconState state) {
      if (nodeStore == null) {
        nodeStore = new KvStoreTreeNodeStore(knownStoredBranchesCache, db, transaction, schema);
      }
      transaction.put(
          schema.getColumnFinalizedStateRootsBySlot(), state.getSlot(), state.hashTreeRoot());
      state
          .getSchema()
          .storeBackingNodes(
              nodeStore,
              MAX_BRANCH_LEVELS_SKIPPED,
              GIndexUtil.SELF_G_INDEX,
              state.getBackingNode());
    }

    @Override
    public void commit() {
      if (nodeStore != null) {
        knownStoredBranchesCache.addAll(nodeStore.getStoredBranchRoots());
        branchNodeStoredCounter.labels("stored").inc(nodeStore.getStoredBranchNodeCount());
        branchNodeStoredCounter.labels("skipped").inc(nodeStore.getSkippedBranchNodeCount());
      }
    }
  }
}
