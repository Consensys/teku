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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.server.kvstore.KvStoreAccessor;
import tech.pegasys.teku.storage.server.kvstore.KvStoreAccessor.KvStoreTransaction;
import tech.pegasys.teku.storage.server.kvstore.MockKvStoreInstance;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.V4FinalizedStateStorageLogic.FinalizedStateUpdater;
import tech.pegasys.teku.storage.server.kvstore.schema.SchemaFinalizedTreeState;
import tech.pegasys.teku.storage.server.kvstore.schema.V6TreeSchemaFinalized;

class V4FinalizedStateTreeStorageLogicTest {

  private final Spec spec = TestSpecFactory.createDefault();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final SchemaFinalizedTreeState schema = new V6TreeSchemaFinalized(spec);
  private final KvStoreAccessor db =
      MockKvStoreInstance.createEmpty(schema.getAllColumns(), schema.getAllVariables());

  private final V4FinalizedStateTreeStorageLogic logic =
      new V4FinalizedStateTreeStorageLogic(new NoOpMetricsSystem(), spec, 1000);

  @Test
  void shouldRoundTripState() {
    final BeaconState state = dataStructureUtil.randomBeaconState();

    storeState(state);
    assertStateReloads(state);
  }

  @Test
  void shouldGetMostRecentStateBeforeRequestedSlot() {
    final BeaconState state = dataStructureUtil.randomBeaconState();

    storeState(state);
    assertStateReloads(state, state.getSlot().plus(10));
  }

  @Test
  void shouldStoreAndLoadMultipleStates() {
    final BeaconState state1 = dataStructureUtil.randomBeaconState(UInt64.valueOf(3));
    final BeaconState state2 = dataStructureUtil.randomBeaconState(UInt64.valueOf(5));
    final BeaconState state3 = dataStructureUtil.randomBeaconState(UInt64.valueOf(7));
    final BeaconState state4 = dataStructureUtil.randomBeaconState(UInt64.valueOf(10));
    try (final KvStoreTransaction transaction = db.startTransaction()) {
      final FinalizedStateUpdater<SchemaFinalizedTreeState> updater = logic.updater();
      updater.addFinalizedState(db, transaction, schema, state1);
      updater.addFinalizedState(db, transaction, schema, state2);
      updater.addFinalizedState(db, transaction, schema, state3);
      updater.addFinalizedState(db, transaction, schema, state4);
      transaction.commit();
    }

    assertStateReloads(state1);
    assertStateReloads(state2);
    assertStateReloads(state3);
    assertStateReloads(state4);
  }

  private void assertStateReloads(final BeaconState state) {
    assertStateReloads(state, state.getSlot());
  }

  private void assertStateReloads(final BeaconState expectedState, final UInt64 slot) {
    final Optional<BeaconState> loadedState =
        logic.getLatestAvailableFinalizedState(db, schema, slot);
    assertThat(loadedState).contains(expectedState);
  }

  private void storeState(final BeaconState state) {
    try (final KvStoreTransaction transaction = db.startTransaction()) {
      logic.updater().addFinalizedState(db, transaction, schema, state);
      transaction.commit();
    }
  }
}
