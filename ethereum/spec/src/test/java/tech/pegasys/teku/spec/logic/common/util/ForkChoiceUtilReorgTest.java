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

package tech.pegasys.teku.spec.logic.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceReorgContext;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.EpochProcessor;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.EpochProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.SlotProcessingException;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class ForkChoiceUtilReorgTest {
  private final Spec spec = TestSpecFactory.createMinimalBellatrix();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final UInt64 slot = UInt64.ONE;

  @Test
  void getProposerHeadReturnsHeadWhenTimely() {
    final ReorgTestSetup setup = new ReorgTestSetup();
    setup.withHeadBlock();
    setup.context.setBlockTimeliness(setup.signedBlockAndState.getRoot(), true);

    assertThat(
            setup.harness.getProposerHead(
                setup.context, setup.signedBlockAndState.getRoot(), UInt64.valueOf(2)))
        .isEqualTo(setup.signedBlockAndState.getRoot());
  }

  @Test
  void getProposerHeadReturnsParentWhenAllChecksPass() {
    final ReorgTestSetup setup = new ReorgTestSetup();
    setup.withHeadBlock();
    setup.context.setBlockTimeliness(setup.signedBlockAndState.getRoot(), false);
    setup.withCurrentSlot(UInt64.ONE);
    setup.withFfgCompetitive();
    setup.withParentSlot(Optional.of(UInt64.ZERO));
    setup.harness.headWeak = true;
    setup.harness.parentStrong = true;

    assertThat(
            setup.harness.getProposerHead(
                setup.context, setup.signedBlockAndState.getRoot(), UInt64.valueOf(2)))
        .isEqualTo(setup.signedBlockAndState.getParentRoot());
  }

  @Test
  void isProposerBoostActiveMatchesLateBlockReorgLogicSemantics() {
    final ReorgTestSetup setup = new ReorgTestSetup();
    final Bytes32 headRoot = setup.signedBlockAndState.getRoot();

    when(setup.store.getProposerBoostRoot()).thenReturn(Optional.of(headRoot));
    assertThat(setup.baseForkChoiceUtil.isProposerBoostActive(setup.store, headRoot)).isFalse();

    when(setup.store.getProposerBoostRoot())
        .thenReturn(Optional.of(dataStructureUtil.randomBytes32()));
    assertThat(setup.baseForkChoiceUtil.isProposerBoostActive(setup.store, headRoot)).isTrue();
  }

  @Test
  void shouldOverrideForkChoiceUpdateReturnsFalseWhenHeadIsTimely() {
    final ReorgTestSetup setup = new ReorgTestSetup();
    setup.withHeadBlock();
    setup.context.setBlockTimeliness(setup.signedBlockAndState.getRoot(), true);

    assertThat(
            setup.harness.shouldOverrideForkChoiceUpdate(
                setup.context, setup.signedBlockAndState.getRoot()))
        .isFalse();
  }

  @Test
  void shouldOverrideForkChoiceUpdateReturnsTrueWhenAllChecksPass() {
    final ReorgTestSetup setup = new ReorgTestSetup();
    setup.withHeadBlock();
    setup.context.setBlockTimeliness(setup.signedBlockAndState.getRoot(), false);
    setup.withCurrentSlot(UInt64.valueOf(2));
    setup.withFfgCompetitive();
    setup.withParentSlot(Optional.of(UInt64.ZERO));
    setup.harness.headWeak = true;
    setup.harness.parentStrong = true;
    when(setup.store.getBlockStateIfAvailable(any()))
        .thenReturn(Optional.of(setup.signedBlockAndState.getState()));
    setup.context.validatorConnected = true;

    assertThat(
            setup.harness.shouldOverrideForkChoiceUpdate(
                setup.context, setup.signedBlockAndState.getRoot()))
        .isTrue();
  }

  @Test
  void isHeadWeakUsesThreshold() {
    final ReorgTestSetup setup = new ReorgTestSetup();
    when(setup.store.isHeadWeak(setup.signedBlockAndState.getRoot())).thenReturn(true);

    assertThat(
            setup.baseForkChoiceUtil.isHeadWeak(
                setup.store, setup.signedBlockAndState.getRoot(), UInt64.valueOf(2)))
        .isTrue();
  }

  @Test
  void isParentStrongDefaultsToTrueWhenNodeMissing() {
    final ReorgTestSetup setup = new ReorgTestSetup();

    assertThat(
            setup.baseForkChoiceUtil.isParentStrong(
                setup.store, setup.signedBlockAndState.getBlock(), UInt64.ONE))
        .isTrue();
  }

  private class ReorgTestSetup {
    private final SignedBlockAndState signedBlockAndState =
        dataStructureUtil.randomSignedBlockAndState(slot);
    private final UInt64 genesisTime = signedBlockAndState.getState().getGenesisTime();
    private final UInt64 genesisTimeMillis = genesisTime.times(1000);
    private final ForkChoiceUtil baseForkChoiceUtil;
    private final ForkChoiceUtilHarness harness;
    private final ReadOnlyStore store = mock(ReadOnlyStore.class);
    private final ReadOnlyForkChoiceStrategy forkChoiceStrategy =
        mock(ReadOnlyForkChoiceStrategy.class);
    private final TestForkChoiceReorgContext context = new TestForkChoiceReorgContext(store);

    private ReorgTestSetup() {
      final SpecVersion specVersion = spec.atSlot(slot);
      baseForkChoiceUtil = specVersion.getForkChoiceUtil();
      harness =
          new ForkChoiceUtilHarness(
              specVersion.getConfig(),
              specVersion.beaconStateAccessors(),
              specVersion.getEpochProcessor(),
              specVersion.getAttestationUtil(),
              specVersion.miscHelpers());

      when(store.getForkChoiceStrategy()).thenReturn(forkChoiceStrategy);
      when(store.getGenesisTime()).thenReturn(genesisTime);
      when(store.getGenesisTimeMillis()).thenReturn(genesisTimeMillis);
      when(store.getTimeInMillis()).thenReturn(genesisTimeMillis);
      when(store.getTimeSeconds()).thenReturn(genesisTime);
      when(store.getFinalizedCheckpoint())
          .thenReturn(dataStructureUtil.randomCheckpoint(UInt64.ZERO));
      when(store.getProposerBoostRoot()).thenReturn(Optional.empty());
      when(store.getBlockIfAvailable(any())).thenReturn(Optional.empty());
      when(store.getBlockStateIfAvailable(any())).thenReturn(Optional.empty());
      when(store.isFfgCompetitive(any(), any())).thenReturn(Optional.empty());
      when(store.isHeadWeak(any())).thenReturn(false);
      when(store.isParentStrong(any())).thenReturn(true);
      when(forkChoiceStrategy.blockSlot(any())).thenReturn(Optional.empty());
    }

    private void withHeadBlock() {
      when(store.getBlockIfAvailable(any())).thenReturn(signedBlockAndState.getSignedBeaconBlock());
    }

    private void withParentSlot(final Optional<UInt64> maybeSlot) {
      when(forkChoiceStrategy.blockSlot(signedBlockAndState.getParentRoot())).thenReturn(maybeSlot);
    }

    private void withFfgCompetitive() {
      when(store.isFfgCompetitive(any(), any())).thenReturn(Optional.of(true));
    }

    private void withCurrentSlot(final UInt64 currentSlot) {
      final UInt64 currentTimeMillis =
          genesisTimeMillis.plus(
              currentSlot.times(spec.getGenesisSpecConfig().getSlotDurationMillis()));
      when(store.getTimeInMillis()).thenReturn(currentTimeMillis);
      when(store.getTimeSeconds()).thenReturn(currentTimeMillis.dividedBy(1000));
    }
  }

  private static class TestForkChoiceReorgContext implements ForkChoiceReorgContext {
    private final ReadOnlyStore store;
    private final Map<Bytes32, ForkChoiceUtil.BlockTimeliness> blockTimeliness = new HashMap<>();
    private boolean validatorConnected = true;

    private TestForkChoiceReorgContext(final ReadOnlyStore store) {
      this.store = store;
    }

    @Override
    public ReadOnlyStore getStore() {
      return store;
    }

    @Override
    public Optional<ForkChoiceUtil.BlockTimeliness> getBlockTimeliness(final Bytes32 root) {
      return Optional.ofNullable(blockTimeliness.get(root));
    }

    @Override
    public boolean isValidatorConnected(final int validatorIndex, final UInt64 slot) {
      return validatorConnected;
    }

    @Override
    public BeaconState processSlots(final BeaconState state, final UInt64 slot)
        throws SlotProcessingException, EpochProcessingException {
      return state;
    }

    private void setBlockTimeliness(final Bytes32 root, final boolean isTimely) {
      blockTimeliness.put(root, new ForkChoiceUtil.BlockTimeliness(isTimely, false));
    }
  }

  private static class ForkChoiceUtilHarness extends ForkChoiceUtil {
    private boolean headWeak;
    private boolean parentStrong;

    private ForkChoiceUtilHarness(
        final SpecConfig specConfig,
        final BeaconStateAccessors beaconStateAccessors,
        final EpochProcessor epochProcessor,
        final AttestationUtil attestationUtil,
        final MiscHelpers miscHelpers) {
      super(specConfig, beaconStateAccessors, epochProcessor, attestationUtil, miscHelpers);
    }

    @Override
    public boolean isHeadWeak(
        final ReadOnlyStore store, final Bytes32 root, final UInt64 reorgThreshold) {
      return headWeak;
    }

    @Override
    public boolean isParentStrong(
        final ReadOnlyStore store, final SignedBeaconBlock head, final UInt64 parentThreshold) {
      return parentStrong;
    }

    @Override
    protected int getProposerIndex(final BeaconState proposerPreState, final UInt64 proposalSlot) {
      return 1;
    }
  }
}
