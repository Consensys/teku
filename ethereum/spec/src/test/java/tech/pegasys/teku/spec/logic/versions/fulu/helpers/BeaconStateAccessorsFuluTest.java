/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.spec.logic.versions.fulu.helpers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.fulu.BeaconStateFulu;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.PredicatesElectra;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsFulu;
import tech.pegasys.teku.storage.server.StateStorageMode;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

public class BeaconStateAccessorsFuluTest {
  private final Spec spec = TestSpecFactory.createMinimalFulu();
  private final PredicatesElectra predicatesElectra =
      new PredicatesElectra(spec.getGenesisSpecConfig());
  final SpecConfigFulu specConfigFulu = spec.getGenesisSpecConfig().toVersionFulu().orElseThrow();
  final SchemaDefinitionsFulu schemaDefinitionsFulu =
      spec.getGenesisSchemaDefinitions().toVersionFulu().orElseThrow();
  private final MiscHelpersFulu miscHelpers =
      new MiscHelpersFulu(specConfigFulu, predicatesElectra, schemaDefinitionsFulu);
  private final BeaconStateAccessorsFulu stateAccessorsFulu =
      new BeaconStateAccessorsFulu(spec.getGenesisSpecConfig(), predicatesElectra, miscHelpers);
  private final StorageSystem storageSystem =
      InMemoryStorageSystemBuilder.buildDefault(StateStorageMode.PRUNE, spec);

  @Test
  void getProposerIndices_notGenesisEpoch() {
    storageSystem.chainUpdater().initializeGenesis();
    final SignedBlockAndState blockAndState = storageSystem.chainUpdater().advanceChain(16);
    final List<Integer> proposerLookahead =
        getProposerLookaheadFromState(blockAndState.getState().toVersionFulu().orElseThrow());
    assertThat(
            stateAccessorsFulu.getBeaconProposerIndices(
                blockAndState.getState(), UInt64.valueOf(2)))
        .isEqualTo(proposerLookahead.subList(0, 8));
    assertThat(
            stateAccessorsFulu.getBeaconProposerIndices(
                blockAndState.getState(), UInt64.valueOf(3)))
        .isEqualTo(proposerLookahead.subList(8, 16));
  }

  @Test
  @Disabled
  void getProposerIndices_genesisEpoch() {
    storageSystem.chainUpdater().initializeGenesis();
    storageSystem.getChainHead();
    final StateAndBlockSummary blockAndState = storageSystem.getChainHead();
    final List<Integer> proposerLookahead =
        getProposerLookaheadFromState(blockAndState.getState().toVersionFulu().orElseThrow());
    assertThat(
            stateAccessorsFulu.getBeaconProposerIndices(
                blockAndState.getState(), UInt64.valueOf(0)))
        .isEqualTo(proposerLookahead.subList(0, 8));
    assertThat(
            stateAccessorsFulu.getBeaconProposerIndices(
                blockAndState.getState(), UInt64.valueOf(1)))
        .isEqualTo(proposerLookahead.subList(8, 16));
  }

  @Test
  void computeProposerEpoch_currentEpoch() {
    storageSystem.chainUpdater().initializeGenesis();
    final SignedBlockAndState blockAndState = storageSystem.chainUpdater().advanceChain(16);
    assertThat(
            stateAccessorsFulu.getBeaconProposerIndex(blockAndState.getState(), UInt64.valueOf(16)))
        .isEqualTo(0);
  }

  @Test
  void computeProposerEpoch_nextEpoch() {
    storageSystem.chainUpdater().initializeGenesis();
    final SignedBlockAndState blockAndState = storageSystem.chainUpdater().advanceChain(16);
    assertThat(
            stateAccessorsFulu.getBeaconProposerIndex(blockAndState.getState(), UInt64.valueOf(25)))
        .isEqualTo(2);
  }

  @Test
  void computeProposerEpoch_futureEpoch() {
    storageSystem.chainUpdater().initializeGenesis();
    final SignedBlockAndState blockAndState = storageSystem.chainUpdater().advanceChain(16);
    assertThatThrownBy(
            () ->
                stateAccessorsFulu.getBeaconProposerIndex(
                    blockAndState.getState(), UInt64.valueOf(33)))
        .hasMessageContaining("out of range");
  }

  private List<Integer> getProposerLookaheadFromState(final BeaconStateFulu beaconStateFulu) {
    return beaconStateFulu.getProposerLookahead().stream()
        .mapToInt(m -> m.get().intValue())
        .boxed()
        .toList();
  }
}
