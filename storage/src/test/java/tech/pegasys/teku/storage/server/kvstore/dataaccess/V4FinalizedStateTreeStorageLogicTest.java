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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.server.kvstore.KvStoreAccessor;
import tech.pegasys.teku.storage.server.kvstore.KvStoreAccessor.KvStoreTransaction;
import tech.pegasys.teku.storage.server.kvstore.MockKvStoreInstance;
import tech.pegasys.teku.storage.server.kvstore.schema.SchemaFinalizedTreeState;
import tech.pegasys.teku.storage.server.kvstore.schema.V6TreeSchemaFinalized;

class V4FinalizedStateTreeStorageLogicTest {

  private final Spec spec = TestSpecFactory.createDefault();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final SchemaFinalizedTreeState schema = new V6TreeSchemaFinalized(spec);
  private final KvStoreAccessor db =
      MockKvStoreInstance.createEmpty(schema.getAllColumns(), schema.getAllVariables());

  private final V4FinalizedStateTreeStorageLogic logic = new V4FinalizedStateTreeStorageLogic(spec);

  @Test
  @Disabled("Need to support iterating supernodes before this can work")
  void shouldRoundTripState() {
    final BeaconState state = dataStructureUtil.randomBeaconState();

    storeState(state);
    final Optional<BeaconState> loadedState =
        logic.getLatestAvailableFinalizedState(db, schema, state.getSlot());
    assertThat(loadedState).contains(state);
  }

  private void storeState(final BeaconState state) {
    try (final KvStoreTransaction transaction = db.startTransaction()) {
      logic.updater().addFinalizedState(db, transaction, schema, state);
      transaction.commit();
    }
  }
}
