/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.storage.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.epbs.SignedExecutionPayloadAndState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class StoreTransactionGloasTest extends AbstractStoreTest {

  @Override
  protected Spec getSpec() {
    return TestSpecFactory.createMinimalGloas();
  }

  @Test
  public void getExecutionPayloadStateIfAvailable_fromTx() {
    final UpdatableStore store = createGenesisStore();
    final SignedBlockAndState blockAndState = chainBuilder.generateNextBlock();
    final Optional<SignedExecutionPayloadAndState> maybeExecutionPayloadAndState =
        chainBuilder.getExecutionPayloadAndStateAtSlot(blockAndState.getSlot());
    assertThat(maybeExecutionPayloadAndState).isPresent();
    final SignedExecutionPayloadAndState executionPayloadAndState =
        maybeExecutionPayloadAndState.get();
    final UpdatableStore.StoreTransaction tx = store.startTransaction(storageUpdateChannel);
    tx.putExecutionPayloadAndState(
        executionPayloadAndState.executionPayload(), executionPayloadAndState.state());

    final Optional<BeaconState> result =
        tx.getExecutionPayloadStateIfAvailable(blockAndState.getRoot());
    assertThat(result).hasValue(executionPayloadAndState.state());
  }
}
