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

package tech.pegasys.teku.spec.logic.versions.gloas.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.fulu.BeaconStateFulu;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.Builder;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.EpochProcessor;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

public class BeaconStateAccessorsGloasTest {

  private final Spec spec = TestSpecFactory.createMinimalGloas();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final BeaconStateAccessorsGloas beaconStateAccessors =
      BeaconStateAccessorsGloas.required(spec.getGenesisSpec().beaconStateAccessors());
  private final StorageSystem storageSystem = InMemoryStorageSystemBuilder.buildDefault(16, spec);

  @Test
  void getBuilderIndex_shouldReturnBuilderIndex() {
    final BeaconStateGloas state = BeaconStateGloas.required(dataStructureUtil.randomBeaconState());
    final SszList<Builder> builders = state.getBuilders();
    assertThat(builders).isNotEmpty();
    for (int i = 0; i < builders.size(); i++) {
      final Builder builder = builders.get(i);
      assertThat(beaconStateAccessors.getBuilderIndex(state, builder.getPublicKey())).contains(i);
    }
  }

  @Test
  public void getBuilderIndex_shouldReturnEmptyWhenBuilderNotFound() {
    final BeaconState state = dataStructureUtil.randomBeaconState();
    final Optional<Integer> index =
        beaconStateAccessors.getBuilderIndex(state, dataStructureUtil.randomPublicKey());
    assertThat(index).isEmpty();
  }

  @Test
  public void getBuilderPubKey_shouldReturnBuilderPubKey() {
    final BeaconStateGloas state = BeaconStateGloas.required(dataStructureUtil.randomBeaconState());
    final SszList<Builder> builders = state.getBuilders();
    assertThat(builders).isNotEmpty();
    for (int i = 0; i < builders.size(); i++) {
      final Builder builder = builders.get(i);
      final UInt64 builderIndex = UInt64.valueOf(i);
      assertThat(beaconStateAccessors.getBuilderPubKey(state, builderIndex))
          .contains(builder.getPublicKey());
      // pubKey => builderIndex mapping is pre cached
      assertThat(
              BeaconStateCache.getTransitionCaches(state)
                  .getBuildersPubKeys()
                  .getCached(builderIndex))
          .isPresent();
    }
  }

  @Test
  public void getBuilderPubKey_shouldReturnEmptyWhenBuilderNotExisting() {
    final BeaconState state = dataStructureUtil.randomBeaconState();
    final Optional<BLSPublicKey> index =
        beaconStateAccessors.getBuilderPubKey(state, UInt64.valueOf(999));
    assertThat(index).isEmpty();
  }

  @Test
  void getBeaconProposerIndices_shouldExcludeSlashedValidators() {
    final StorageSystem storageSystem = InMemoryStorageSystemBuilder.buildDefault(16, spec);
    storageSystem.chainUpdater().initializeGenesis();
    final BeaconState genesisState = storageSystem.getChainHead().getState();
    final UInt64 epoch = UInt64.ZERO;

    // Get proposer indices before slashing
    final List<Integer> proposersBefore =
        beaconStateAccessors.getBeaconProposerIndices(genesisState, epoch);
    assertThat(proposersBefore).isNotEmpty();

    // Pick a validator that is selected as proposer
    final int slashedIndex = proposersBefore.getFirst();

    // Slash that validator
    final BeaconState stateWithSlashedValidator =
        genesisState.updated(
            mutableState -> {
              final Validator validator = mutableState.getValidators().get(slashedIndex);
              mutableState.getValidators().set(slashedIndex, validator.withSlashed(true));
            });

    // Get proposer indices after slashing
    final List<Integer> proposersAfter =
        beaconStateAccessors.getBeaconProposerIndices(stateWithSlashedValidator, epoch);

    // Slashed validator should not appear in the proposer list
    assertThat(proposersAfter).doesNotContain(slashedIndex);
    assertThat(proposersAfter).hasSameSizeAs(proposersBefore);
  }

  @Test
  void processProposerLookahead_shouldExcludeSlashedValidatorsFromLookahead() {
    final StorageSystem storageSystem = InMemoryStorageSystemBuilder.buildDefault(16, spec);
    storageSystem.chainUpdater().initializeGenesis();
    final BeaconState genesisState = storageSystem.getChainHead().getState();

    final int slotsPerEpoch = spec.getGenesisSpecConfig().getSlotsPerEpoch();
    final int minSeedLookahead = spec.getGenesisSpecConfig().getMinSeedLookahead();

    // The epoch that processProposerLookahead will compute proposers for
    final UInt64 targetEpoch =
        beaconStateAccessors.getCurrentEpoch(genesisState).plus(minSeedLookahead).plus(1);

    // Find which validators would be selected as proposers for the target epoch
    final List<Integer> expectedProposers =
        beaconStateAccessors.getBeaconProposerIndices(genesisState, targetEpoch);
    final int slashedIndex = expectedProposers.getFirst();

    // Slash that validator and run processProposerLookahead
    final BeaconState stateAfterProcessing =
        genesisState.updated(
            mutableState -> {
              final Validator validator = mutableState.getValidators().get(slashedIndex);
              mutableState.getValidators().set(slashedIndex, validator.withSlashed(true));

              final EpochProcessor epochProcessor = spec.getGenesisSpec().getEpochProcessor();
              epochProcessor.processProposerLookahead(mutableState);
            });

    // Extract the last epoch of the proposer lookahead (the newly computed entries)
    final BeaconStateFulu stateFulu = BeaconStateFulu.required(stateAfterProcessing);
    final List<Integer> newLookaheadProposers =
        stateFulu.getProposerLookahead().stream()
            .skip(stateFulu.getProposerLookahead().size() - slotsPerEpoch)
            .map(SszUInt64::get)
            .map(UInt64::intValue)
            .toList();

    // Slashed validator should NOT appear in the newly computed proposer lookahead
    assertThat(newLookaheadProposers).doesNotContain(slashedIndex);
    assertThat(newLookaheadProposers).hasSize(slotsPerEpoch);
  }

  @Test
  public void getNextSyncCommitteeIndices_shouldExcludeSlashedValidators() {
    storageSystem.chainUpdater().initializeGenesis();
    final BeaconState genesisState = storageSystem.getChainHead().getState();
    final int slashedIndex = 0;
    final BeaconState state =
        genesisState.updated(
            mutableState -> {
              final Validator validator = mutableState.getValidators().get(slashedIndex);
              mutableState.getValidators().set(slashedIndex, validator.withSlashed(true));
            });

    final IntList syncCommitteeIndices = beaconStateAccessors.getNextSyncCommitteeIndices(state);

    assertThat(syncCommitteeIndices.size()).isGreaterThan(0);
    assertThat(syncCommitteeIndices.contains(slashedIndex)).isFalse();
  }

  @Test
  public void getNextSyncCommitteeIndices_shouldExcludeMultipleSlashedValidators() {
    storageSystem.chainUpdater().initializeGenesis();
    final BeaconState genesisState = storageSystem.getChainHead().getState();
    final int[] slashedIndices = {0, 1, 2};
    final BeaconState state =
        genesisState.updated(
            mutableState -> {
              for (int idx : slashedIndices) {
                final Validator validator = mutableState.getValidators().get(idx);
                mutableState.getValidators().set(idx, validator.withSlashed(true));
              }
            });

    final IntList syncCommitteeIndices = beaconStateAccessors.getNextSyncCommitteeIndices(state);

    assertThat(syncCommitteeIndices.size()).isGreaterThan(0);
    for (int slashedIdx : slashedIndices) {
      assertThat(syncCommitteeIndices.contains(slashedIdx)).isFalse();
    }
  }
}
