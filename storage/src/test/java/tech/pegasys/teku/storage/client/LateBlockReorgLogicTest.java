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

package tech.pegasys.teku.storage.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.store.UpdatableStore;

class LateBlockReorgLogicTest {
  private final Spec spec = TestSpecFactory.createDefault();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final int millisPerSlot = spec.getGenesisSpecConfig().getSlotDurationMillis();
  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private final UInt64 slot = UInt64.ONE;

  private StubTimeProvider timeProvider;
  private Bytes32 blockRoot;
  private SignedBlockAndState signedBlockAndState;
  private LateBlockReorgLogicInstrumented reorgLogicInstrumented;

  private final UpdatableStore store = mock(UpdatableStore.class);

  @BeforeEach
  void setup() {
    signedBlockAndState = dataStructureUtil.randomSignedBlockAndState(slot);
    blockRoot = signedBlockAndState.getBlock().getMessage().getRoot();
    timeProvider =
        StubTimeProvider.withTimeInSeconds(signedBlockAndState.getState().getGenesisTime());
    reorgLogicInstrumented =
        new LateBlockReorgLogicInstrumented(spec, recentChainData, () -> timeProvider);

    when(recentChainData.getGenesisTime())
        .thenReturn(signedBlockAndState.getState().getGenesisTime());
    when(recentChainData.getGenesisTimeMillis())
        .thenReturn(signedBlockAndState.getState().getGenesisTime().times(1000));
    when(recentChainData.getCurrentSlot()).thenReturn(Optional.of(UInt64.ONE));
    when(recentChainData.getStore()).thenReturn(store);

    when(store.getFinalizedCheckpoint())
        .thenReturn(dataStructureUtil.randomCheckpoint(UInt64.ZERO));
  }

  @Test
  void blockTimeliness_shouldReportTimelinessIfSet() {
    final UInt64 computedTime = computeTime(slot, 500);

    reorgLogicInstrumented.setBlockTimelinessFromArrivalTime(
        signedBlockAndState.getBlock(), computedTime);
    assertThat(reorgLogicInstrumented.isBlockTimely(blockRoot)).contains(true);
    assertThat(reorgLogicInstrumented.isBlockLate(blockRoot)).isFalse();
  }

  @Test
  void blockTimeliness_shouldSetTimelinessOnce() {
    final UInt64 computedTime = computeTime(slot, 500);

    reorgLogicInstrumented.setBlockTimelinessFromArrivalTime(
        signedBlockAndState.getBlock(), computedTime);
    // The block would be late in the tracker if this set is not ignored, but it should be ignored
    // because its already been set
    reorgLogicInstrumented.setBlockTimelinessFromArrivalTime(
        signedBlockAndState.getBlock(), computedTime.plus(3000));
    assertThat(reorgLogicInstrumented.isBlockTimely(blockRoot)).contains(true);
    assertThat(reorgLogicInstrumented.isBlockLate(blockRoot)).isFalse();
  }

  @Test
  void blockTimeliness_shouldReportFalseIfLate() {
    final UInt64 computedTime = computeTime(slot, 2100);

    reorgLogicInstrumented.setBlockTimelinessFromArrivalTime(
        signedBlockAndState.getBlock(), computedTime);
    assertThat(reorgLogicInstrumented.isBlockTimely(blockRoot)).contains(false);
    assertThat(reorgLogicInstrumented.isBlockLate(blockRoot)).isTrue();
  }

  @Test
  void blockTimeliness_shouldReportFalseIfAtLimit() {
    final UInt64 computedTime = computeTime(slot, 2000);

    reorgLogicInstrumented.setBlockTimelinessFromArrivalTime(
        signedBlockAndState.getBlock(), computedTime);
    assertThat(reorgLogicInstrumented.isBlockTimely(blockRoot)).contains(false);
    assertThat(reorgLogicInstrumented.isBlockLate(blockRoot)).isTrue();
  }

  @Test
  void blockTimeliness_shouldReportEmptyIfNotSet() {
    assertThat(reorgLogicInstrumented.isBlockTimely(blockRoot)).isEmpty();
    assertThat(reorgLogicInstrumented.isBlockLate(blockRoot)).isFalse();
  }

  @Test
  void isProposingOnTime_shouldDetectBeforeSlotStartAsOk() {
    // Advance time to 500ms before slot start
    timeProvider.advanceTimeByMillis(millisPerSlot - 500);
    assertThat(reorgLogicInstrumented.isProposingOnTime(slot)).isTrue();
  }

  @Test
  void isProposingOnTime_shouldDetectSlotStartAsOnTime() {
    // Advance time by 1 slot, leaving us at exactly slot time
    timeProvider.advanceTimeByMillis(millisPerSlot);
    assertThat(reorgLogicInstrumented.isProposingOnTime(slot)).isTrue();
  }

  @Test
  void isProposingOnTime_shouldDetectLateIfAttestationsDue() {
    // attestation is due 2 seconds into slot
    timeProvider.advanceTimeByMillis(millisPerSlot + 2000);
    assertThat(reorgLogicInstrumented.isProposingOnTime(slot)).isFalse();
  }

  @Test
  void isProposingOnTime_shouldDetectOnTimeBeforeCutoff() {
    /// 1000 ms into slot, cutoff is 1001ms
    timeProvider.advanceTimeByMillis(millisPerSlot + 1000);
    assertThat(reorgLogicInstrumented.isProposingOnTime(slot)).isTrue();
  }

  @Test
  void isProposingOnTime_shouldDetectLateIfHalfWayToAttestationDue() {
    timeProvider.advanceTimeByMillis(millisPerSlot + 1001);
    assertThat(reorgLogicInstrumented.isProposingOnTime(slot)).isFalse();
  }

  @Test
  void shouldOverrideFcuCheckWeights_MultiSlotReorg() {
    withLateBlock(blockRoot);
    withParentSlot(Optional.of(UInt64.ZERO));
    withHeadBlock();
    when(store.isHeadWeak(any())).thenReturn(true);
    when(store.isParentStrong(any())).thenReturn(false);

    // because we're at head slot 1, slot 3 is too far ahead to be allowing a single slot reorg
    assertThat(
            reorgLogicInstrumented.shouldOverrideFcuCheckWeights(
                signedBlockAndState.getSignedBeaconBlock().orElseThrow(),
                blockRoot,
                UInt64.valueOf(3),
                UInt64.valueOf(3)))
        .isFalse();
  }

  @Test
  void shouldOverrideFcuCheckWeights_weakHead() {
    withLateBlock(blockRoot);
    withParentSlot(Optional.of(UInt64.ZERO));
    withHeadBlock();
    when(store.isHeadWeak(any())).thenReturn(true);
    when(store.isParentStrong(any())).thenReturn(true);

    assertThat(
            reorgLogicInstrumented.shouldOverrideFcuCheckWeights(
                signedBlockAndState.getSignedBeaconBlock().orElseThrow(),
                blockRoot,
                UInt64.valueOf(2),
                UInt64.valueOf(2)))
        .isTrue();
  }

  @Test
  void shouldOverrideFcuCheckWeights_strongHead() {
    withLateBlock(blockRoot);
    withParentSlot(Optional.of(UInt64.ZERO));
    withHeadBlock();
    when(store.isHeadWeak(any())).thenReturn(false);
    when(store.isParentStrong(any())).thenReturn(true);

    assertThat(
            reorgLogicInstrumented.shouldOverrideFcuCheckWeights(
                signedBlockAndState.getSignedBeaconBlock().orElseThrow(),
                blockRoot,
                UInt64.valueOf(2),
                UInt64.valueOf(2)))
        .isFalse();
  }

  @Test
  void shouldOverrideFcuCheckWeights_weakParent() {
    withLateBlock(blockRoot);
    withParentSlot(Optional.of(UInt64.ZERO));
    withHeadBlock();
    when(store.isHeadWeak(any())).thenReturn(true);
    when(store.isParentStrong(any())).thenReturn(false);

    assertThat(
            reorgLogicInstrumented.shouldOverrideFcuCheckWeights(
                signedBlockAndState.getSignedBeaconBlock().orElseThrow(),
                blockRoot,
                UInt64.valueOf(2),
                UInt64.valueOf(2)))
        .isFalse();
  }

  @Test
  void isSingleSlotReorg_whenTrue() {
    withTimelyBlock(blockRoot);
    when(recentChainData.getSlotForBlockRoot(signedBlockAndState.getParentRoot()))
        .thenReturn(Optional.of(UInt64.ZERO));
    assertThat(
            reorgLogicInstrumented.isSingleSlotReorg(
                signedBlockAndState.getBlock(), UInt64.valueOf(2)))
        .isTrue();
  }

  @Test
  void isSingleSlotReorg_whenFalse() {
    withTimelyBlock(blockRoot);
    when(recentChainData.getSlotForBlockRoot(signedBlockAndState.getParentRoot()))
        .thenReturn(Optional.of(UInt64.ZERO));
    assertThat(
            reorgLogicInstrumented.isSingleSlotReorg(
                signedBlockAndState.getBlock(), UInt64.valueOf(3)))
        .isFalse();
  }

  @Test
  void isProposerBoostActive() {
    when(store.getProposerBoostRoot()).thenReturn(Optional.of(blockRoot));
    assertThat(reorgLogicInstrumented.isProposerBoostActive(blockRoot)).isFalse();
    assertThat(reorgLogicInstrumented.isProposerBoostActive(dataStructureUtil.randomBytes32()))
        .isTrue();
  }

  @ParameterizedTest
  @MethodSource("stableForkChoiceTests")
  void isForkChoiceStableAndFinalizationOk(final int slot, final boolean expectation) {
    withTimelyBlock(blockRoot);
    assertThat(reorgLogicInstrumented.isForkChoiceStableAndFinalizationOk(UInt64.valueOf(slot)))
        .isEqualTo(expectation);
  }

  @ParameterizedTest
  @MethodSource("isMissingDataTests")
  void isMissingData(
      final Optional<SignedBeaconBlock> maybeBlock,
      final Optional<UInt64> maybeCurrentSlot,
      final boolean expectedResult) {
    assertThat(reorgLogicInstrumented.isMissingData(maybeBlock, maybeCurrentSlot))
        .isEqualTo(expectedResult);
  }

  @Test
  void getProposerHead_boostActiveShortCircuitsGetProposerHead() {
    withLateBlock(blockRoot);
    withHeadBlock();
    withProposerBoostRoot(dataStructureUtil.randomBytes32());
    assertThat(reorgLogicInstrumented.getProposerHead(blockRoot, UInt64.ONE)).isEqualTo(blockRoot);
  }

  @Test
  void getProposerHead_isHeadOnTimeShortCircuitsGetProposerHead() {
    withTimelyBlock(blockRoot);
    withHeadBlock();
    withProposerBoostRoot(null);
    assertThat(reorgLogicInstrumented.getProposerHead(blockRoot, UInt64.ONE)).isEqualTo(blockRoot);
  }

  @Test
  void getProposerHead_shufflingNotStable() {
    withLateBlock(blockRoot);
    withHeadBlock();
    when(store.getProposerBoostRoot()).thenReturn(Optional.empty());
    when(store.getFinalizedCheckpoint())
        .thenReturn(dataStructureUtil.randomCheckpoint(UInt64.ZERO));

    assertThat(reorgLogicInstrumented.getProposerHead(blockRoot, UInt64.valueOf(8)))
        .isEqualTo(blockRoot);
  }

  @Test
  void getProposerHead_withoutHeadBlockInStore() {
    withLateBlock(blockRoot);
    when(store.getProposerBoostRoot()).thenReturn(Optional.empty());
    when(store.getFinalizedCheckpoint())
        .thenReturn(dataStructureUtil.randomCheckpoint(UInt64.ZERO));
    assertThat(reorgLogicInstrumented.getProposerHead(blockRoot, UInt64.ONE)).isEqualTo(blockRoot);
  }

  @Test
  void getProposerHead_finalizationNotOk() {
    withLateBlock(blockRoot);
    withHeadBlock();
    when(store.getProposerBoostRoot()).thenReturn(Optional.empty());
    when(store.getFinalizedCheckpoint())
        .thenReturn(dataStructureUtil.randomCheckpoint(UInt64.ZERO));

    assertThat(reorgLogicInstrumented.getProposerHead(blockRoot, UInt64.valueOf(25)))
        .isEqualTo(blockRoot);
  }

  @Test
  void getProposerHead_ffgNotCompetitive() {
    getProposerHeadPassFirstGate();
    withFfgNotCompetetive();
    withParentSlot(Optional.of(UInt64.ZERO));
    assertThat(reorgLogicInstrumented.getProposerHead(blockRoot, UInt64.valueOf(2)))
        .isEqualTo(blockRoot);
  }

  @Test
  void getProposerHead_notSingleSlotReorgBecauseParentSlot() {
    getProposerHeadPassFirstGate();
    withFfgIsCompetetive();
    assertThat(reorgLogicInstrumented.getProposerHead(blockRoot, UInt64.valueOf(2)))
        .isEqualTo(blockRoot);
  }

  @Test
  void getProposerHead_notSingleSlotReorgBecauseCurrentSlot() {
    getProposerHeadPassFirstGate();
    withParentSlot(Optional.of(UInt64.ZERO));
    withFfgIsCompetetive();
    assertThat(reorgLogicInstrumented.getProposerHead(blockRoot, UInt64.valueOf(3)))
        .isEqualTo(blockRoot);
  }

  @Test
  void getProposerHead_shouldGiveParentRoot() {
    getProposerHeadPassSecondGate();
    when(store.isHeadWeak(any())).thenReturn(true);
    when(store.isParentStrong(any())).thenReturn(true);
    assertThat(reorgLogicInstrumented.getProposerHead(blockRoot, UInt64.valueOf(2)))
        .isEqualTo(signedBlockAndState.getBlock().getParentRoot());
  }

  @Test
  void getProposerHead_isHeadStrong() {
    getProposerHeadPassSecondGate();
    when(store.isHeadWeak(any())).thenReturn(false);
    when(store.isParentStrong(any())).thenReturn(true);
    assertThat(reorgLogicInstrumented.getProposerHead(blockRoot, UInt64.valueOf(2)))
        .isEqualTo(blockRoot);
  }

  @Test
  void getProposerHead_isParentWeak() {
    getProposerHeadPassSecondGate();
    when(store.isHeadWeak(any())).thenReturn(true);
    when(store.isParentStrong(any())).thenReturn(false);
    assertThat(reorgLogicInstrumented.getProposerHead(blockRoot, UInt64.valueOf(2)))
        .isEqualTo(blockRoot);
  }

  @Test
  void shouldOverrideForkChoice_headOnTime() {
    withTimelyBlock(blockRoot);
    withHeadBlock();
    assertThat(reorgLogicInstrumented.shouldOverrideForkChoiceUpdate(blockRoot)).isFalse();
  }

  @Test
  void shouldOverrideForkChoice_headBlockMissing() {
    withLateBlock(blockRoot);
    withCurrentSlot(Optional.of(UInt64.ONE));
    assertThat(reorgLogicInstrumented.shouldOverrideForkChoiceUpdate(blockRoot)).isFalse();
  }

  @Test
  void shouldOverrideForkChoice_ffgNotCompetetive() {
    shouldOverrideForkChoicePassFirstGate();
    withParentSlot(Optional.of(UInt64.ZERO));
    withFfgNotCompetetive();
    assertThat(reorgLogicInstrumented.shouldOverrideForkChoiceUpdate(blockRoot)).isFalse();
  }

  @Test
  void shouldOverrideForkChoice_parentSlotMissing() {
    shouldOverrideForkChoicePassFirstGate();
    withFfgIsCompetetive();
    assertThat(reorgLogicInstrumented.shouldOverrideForkChoiceUpdate(blockRoot)).isFalse();
  }

  @Test
  void shouldOverrideFcuCheckProposerPreState_preStateMissing() {
    when(store.getBlockStateIfAvailable(any())).thenReturn(Optional.empty());
    assertThat(
            reorgLogicInstrumented.shouldOverrideFcuCheckProposerPreState(
                UInt64.valueOf(2), dataStructureUtil.randomBytes32()))
        .isFalse();
  }

  @Test
  void shouldOverrideFcuCheckProposerPreState_validatorNotConnected() {

    final Optional<BeaconState> maybeParentState = Optional.of(signedBlockAndState.getState());
    when(store.getBlockStateIfAvailable(any())).thenReturn(maybeParentState);
    when(recentChainData.isValidatorConnected(anyInt(), any())).thenReturn(false);
    assertThat(
            reorgLogicInstrumented.shouldOverrideFcuCheckProposerPreState(
                UInt64.valueOf(2), dataStructureUtil.randomBytes32()))
        .isFalse();
  }

  @Test
  void shouldOverrideFcuCheckProposerPreState_validatorIsConnected() {

    final Optional<BeaconState> maybeParentState = Optional.of(signedBlockAndState.getState());
    when(store.getBlockStateIfAvailable(any())).thenReturn(maybeParentState);
    when(recentChainData.isValidatorConnected(anyInt(), any())).thenReturn(true);
    assertThat(
            reorgLogicInstrumented.shouldOverrideFcuCheckProposerPreState(
                UInt64.valueOf(2), dataStructureUtil.randomBytes32()))
        .isTrue();
  }

  private void shouldOverrideForkChoicePassFirstGate() {
    withLateBlock(blockRoot);
    withCurrentSlot(Optional.of(UInt64.ONE));
    withHeadBlock();
  }

  private void getProposerHeadPassFirstGate() {
    withLateBlock(blockRoot);
    withHeadBlock();
    when(store.getProposerBoostRoot()).thenReturn(Optional.empty());
    when(store.getFinalizedCheckpoint())
        .thenReturn(dataStructureUtil.randomCheckpoint(UInt64.ZERO));
  }

  private void getProposerHeadPassSecondGate() {
    getProposerHeadPassFirstGate();
    withFfgIsCompetetive();
    withParentSlot(Optional.of(UInt64.ZERO));
  }

  private UInt64 computeTime(final UInt64 slot, final long timeIntoSlot) {
    return timeProvider.getTimeInMillis().plus(slot.times(millisPerSlot)).plus(timeIntoSlot);
  }

  private void withParentSlot(final Optional<UInt64> maybeSlot) {
    when(recentChainData.getSlotForBlockRoot(any())).thenReturn(maybeSlot);
  }

  private void withCurrentSlot(final Optional<UInt64> maybeSlot) {
    when(recentChainData.getCurrentSlot()).thenReturn(maybeSlot);
  }

  private void withHeadBlock() {
    when(store.getBlockIfAvailable(any())).thenReturn(signedBlockAndState.getSignedBeaconBlock());
  }

  private void withFfgIsCompetetive() {
    when(store.isFfgCompetitive(any(), any())).thenReturn(Optional.of(true));
  }

  private void withFfgNotCompetetive() {
    when(store.isFfgCompetitive(any(), any())).thenReturn(Optional.of(false));
  }

  private void withTimelyBlock(final Bytes32 blockRoot) {
    reorgLogicInstrumented.setBlockTimeliness(blockRoot, true);
  }

  private void withLateBlock(final Bytes32 blockRoot) {
    reorgLogicInstrumented.setBlockTimeliness(blockRoot, false);
  }

  private void withProposerBoostRoot(final Bytes32 boostRoot) {
    when(store.getProposerBoostRoot()).thenReturn(Optional.ofNullable(boostRoot));
  }

  public static Stream<Arguments> isMissingDataTests() {
    final DataStructureUtil dataStructureUtil =
        new DataStructureUtil(TestSpecFactory.createDefault());
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock();
    final ArrayList<Arguments> args = new ArrayList<>();
    args.add(Arguments.of(Optional.of(block), Optional.of(UInt64.ZERO), false));
    args.add(Arguments.of(Optional.empty(), Optional.of(UInt64.ZERO), true));
    args.add(Arguments.of(Optional.of(block), Optional.empty(), true));
    args.add(Arguments.of(Optional.empty(), Optional.empty(), true));
    return args.stream();
  }

  public static Stream<Arguments> stableForkChoiceTests() {
    final ArrayList<Arguments> args = new ArrayList<>();
    args.add(Arguments.of(0, false));
    args.add(Arguments.of(1, true));
    args.add(Arguments.of(7, true));
    args.add(Arguments.of(8, false));
    args.add(Arguments.of(23, true)); // last slot where it's ok based on finalization
    args.add(Arguments.of(24, false)); // boundary of epoch
    args.add(Arguments.of(25, false)); // because finalization no longer ok
    return args.stream();
  }

  @Test
  void equivocationDetection_singleBlockNoEquivocation() {
    final SignedBeaconBlock block = createBlock(UInt64.ONE, UInt64.valueOf(42));
    reorgLogicInstrumented.setBlockTimelinessFromArrivalTime(block, computeTime(slot, 500));
    assertThat(reorgLogicInstrumented.isProposerEquivocation(block.getRoot())).isFalse();
  }

  @Test
  void equivocationDetection_twoBlocksSameSlotSameProposerDifferentRoots() {
    final UInt64 proposer = UInt64.valueOf(42);
    final SignedBeaconBlock block1 = createBlock(UInt64.ONE, proposer);
    final SignedBeaconBlock block2 = createBlock(UInt64.ONE, proposer);
    // Ensure they have different roots
    assertThat(block1.getRoot()).isNotEqualTo(block2.getRoot());

    reorgLogicInstrumented.setBlockTimelinessFromArrivalTime(block1, computeTime(slot, 500));
    reorgLogicInstrumented.setBlockTimelinessFromArrivalTime(block2, computeTime(slot, 600));

    assertThat(reorgLogicInstrumented.isProposerEquivocation(block1.getRoot())).isTrue();
    assertThat(reorgLogicInstrumented.isProposerEquivocation(block2.getRoot())).isTrue();
  }

  @Test
  void equivocationDetection_twoBlocksSameSlotDifferentProposers() {
    final SignedBeaconBlock block1 = createBlock(UInt64.ONE, UInt64.valueOf(42));
    final SignedBeaconBlock block2 = createBlock(UInt64.ONE, UInt64.valueOf(43));

    reorgLogicInstrumented.setBlockTimelinessFromArrivalTime(block1, computeTime(slot, 500));
    reorgLogicInstrumented.setBlockTimelinessFromArrivalTime(block2, computeTime(slot, 600));

    assertThat(reorgLogicInstrumented.isProposerEquivocation(block1.getRoot())).isFalse();
    assertThat(reorgLogicInstrumented.isProposerEquivocation(block2.getRoot())).isFalse();
  }

  @Test
  void equivocationDetection_twoBlocksSameProposerDifferentSlots() {
    final UInt64 proposer = UInt64.valueOf(42);
    final SignedBeaconBlock block1 = createBlock(UInt64.ONE, proposer);
    final SignedBeaconBlock block2 = createBlock(UInt64.valueOf(2), proposer);

    reorgLogicInstrumented.setBlockTimelinessFromArrivalTime(block1, computeTime(slot, 500));
    reorgLogicInstrumented.setBlockTimelinessFromArrivalTime(block2, computeTime(slot, 600));

    assertThat(reorgLogicInstrumented.isProposerEquivocation(block1.getRoot())).isFalse();
    assertThat(reorgLogicInstrumented.isProposerEquivocation(block2.getRoot())).isFalse();
  }

  @Test
  void equivocationDetection_sameBlockRecordedTwice() {
    final SignedBeaconBlock block = createBlock(UInt64.ONE, UInt64.valueOf(42));

    reorgLogicInstrumented.setBlockTimelinessFromArrivalTime(block, computeTime(slot, 500));
    reorgLogicInstrumented.setBlockTimelinessFromArrivalTime(block, computeTime(slot, 600));

    assertThat(reorgLogicInstrumented.isProposerEquivocation(block.getRoot())).isFalse();
  }

  @Test
  void getProposerHead_equivocationPathReorgsWhenHeadWeakAndTimingOk() {
    // Head is NOT late (standard path gate closed), but equivocation detected
    withTimelyBlock(blockRoot);
    withHeadBlock();
    withProposerBoostRoot(null);
    reorgLogicInstrumented.addEquivocatingRoot(blockRoot);
    when(store.isHeadWeak(blockRoot)).thenReturn(true);

    // slot = head.slot + 1 = 2
    assertThat(reorgLogicInstrumented.getProposerHead(blockRoot, UInt64.valueOf(2)))
        .isEqualTo(signedBlockAndState.getBlock().getParentRoot());
  }

  @Test
  void getProposerHead_equivocationPathReturnHeadWhenHeadStrong() {
    withTimelyBlock(blockRoot);
    withHeadBlock();
    withProposerBoostRoot(null);
    reorgLogicInstrumented.addEquivocatingRoot(blockRoot);
    when(store.isHeadWeak(blockRoot)).thenReturn(false);

    assertThat(reorgLogicInstrumented.getProposerHead(blockRoot, UInt64.valueOf(2)))
        .isEqualTo(blockRoot);
  }

  @Test
  void getProposerHead_equivocationPathReturnHeadWhenTimingWrong() {
    withTimelyBlock(blockRoot);
    withHeadBlock();
    withProposerBoostRoot(null);
    reorgLogicInstrumented.addEquivocatingRoot(blockRoot);
    when(store.isHeadWeak(blockRoot)).thenReturn(true);

    // slot = 3 != head.slot + 1 = 2, so timing condition fails
    assertThat(reorgLogicInstrumented.getProposerHead(blockRoot, UInt64.valueOf(3)))
        .isEqualTo(blockRoot);
  }

  @Test
  void getProposerHead_noEquivocationAndHeadWeakReturnHead() {
    withTimelyBlock(blockRoot);
    withHeadBlock();
    withProposerBoostRoot(null);
    when(store.isHeadWeak(blockRoot)).thenReturn(true);

    // No equivocation added, head is timely (standard path fails), equivocation path has no match
    assertThat(reorgLogicInstrumented.getProposerHead(blockRoot, UInt64.valueOf(2)))
        .isEqualTo(blockRoot);
  }

  private SignedBeaconBlock createBlock(final UInt64 blockSlot, final UInt64 proposerIndex) {
    final var body = dataStructureUtil.randomBeaconBlockBody(blockSlot);
    final BeaconBlock beaconBlock =
        new BeaconBlock(
            spec.atSlot(blockSlot).getSchemaDefinitions().getBeaconBlockSchema(),
            blockSlot,
            proposerIndex,
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomBytes32(),
            body);
    return dataStructureUtil.signedBlock(beaconBlock);
  }

  static class LateBlockReorgLogicInstrumented extends LateBlockReorgLogic {
    public LateBlockReorgLogicInstrumented(
        final Spec spec,
        final RecentChainData recentChainData,
        final Supplier<TimeProvider> timeProviderSupplier) {
      super(spec, recentChainData, timeProviderSupplier);
    }

    @Override
    protected int getProposerIndex(final BeaconState proposerPreState, final UInt64 proposalSlot) {
      return 1;
    }

    public void setBlockTimeliness(final Bytes32 root, final boolean isTimely) {
      blockTimeliness.put(root, isTimely);
    }

    public void addEquivocatingRoot(final Bytes32 root) {
      equivocatingBlockRoots.put(root, UInt64.ZERO);
    }
  }
}
