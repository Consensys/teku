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

package tech.pegasys.teku.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.server.StateStorageMode;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

class SpecTest {
  final Spec spec = TestSpecFactory.createMinimalAltair();
  final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final StorageSystem storageSystem =
      InMemoryStorageSystemBuilder.buildDefault(StateStorageMode.PRUNE, spec);
  private final ChainBuilder chainBuilder = storageSystem.chainBuilder();

  @Test
  void shouldWindStateForwardIfOutsidePeriod() {
    chainBuilder.generateGenesis();
    chainBuilder.generateBlocksUpToSlot(8);
    final List<SignedBlockAndState> chain = chainBuilder.finalizeCurrentChain(Optional.empty());

    // the important thing here is that we get a response, we don't fail, even though slot 100 is
    // well beyond the finalized slot (in epoch 4)
    assertThat(
            spec.computeSubnetForAttestation(
                chain.getLast().getState(), dataStructureUtil.randomAttestation(100)))
        .isEqualTo(48);
  }
}
