/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.spec.util;

// TODO(#3658) - Migrate ChainBuilder so we can uncomment this test
class ForkChoiceUtilTest {

  //  private static final UInt64 GENESIS_TIME = UInt64.valueOf("1591924193");
  //  protected static final List<BLSKeyPair> VALIDATOR_KEYS = BLSKeyGenerator.generateKeyPairs(16);
  //  private final ChainBuilder chainBuilder = ChainBuilder.create(VALIDATOR_KEYS);
  //
  //  private final SpecProvider specProvider = StubSpecProvider.create();
  //  private final ForkChoiceUtil forkChoiceUtil =
  // specProvider.getGenesisSpec().getForkChoiceUtil();
  //  private final ChainBuilderForkChoiceStrategy forkChoiceStrategy =
  //      new ChainBuilderForkChoiceStrategy(chainBuilder);
  //  private final UInt64 slot50Time =
  //      GENESIS_TIME.plus(specProvider.getGenesisSpecConstants().getSecondsPerSlot() * 50L);
  //
  //  @BeforeEach
  //  public void setup() {
  //    chainBuilder.generateGenesis();
  //  }
  //
  //  @Test
  //  void getAncestors_shouldGetSimpleSequenceOfAncestors() {
  //    chainBuilder.generateBlocksUpToSlot(10);
  //
  //    final NavigableMap<UInt64, Bytes32> rootsBySlot =
  //        forkChoiceUtil.getAncestors(
  //            forkChoiceStrategy,
  //            chainBuilder.getLatestBlockAndState().getRoot(),
  //            UInt64.ONE,
  //            UInt64.ONE,
  //            UInt64.valueOf(8));
  //
  //    assertThat(rootsBySlot).containsExactlyEntriesOf(getRootsForBlocks(1, 2, 3, 4, 5, 6, 7, 8));
  //  }
  //
  //  @Test
  //  void getAncestors_shouldGetSequenceOfRootsWhenSkipping() {
  //    chainBuilder.generateBlocksUpToSlot(10);
  //
  //    final NavigableMap<UInt64, Bytes32> rootsBySlot =
  //        forkChoiceUtil.getAncestors(
  //            forkChoiceStrategy,
  //            chainBuilder.getLatestBlockAndState().getRoot(),
  //            UInt64.ONE,
  //            UInt64.valueOf(2),
  //            UInt64.valueOf(4));
  //
  //    assertThat(rootsBySlot).containsExactlyEntriesOf(getRootsForBlocks(1, 3, 5, 7));
  //  }
  //
  //  @Test
  //  void getAncestors_shouldGetSequenceOfRootsWhenStartIsPriorToFinalizedCheckpoint() {
  //    chainBuilder.generateBlocksUpToSlot(10);
  //    forkChoiceStrategy.prune(UInt64.valueOf(4));
  //
  //    final NavigableMap<UInt64, Bytes32> rootsBySlot =
  //        forkChoiceUtil.getAncestors(
  //            forkChoiceStrategy,
  //            chainBuilder.getLatestBlockAndState().getRoot(),
  //            UInt64.ONE,
  //            UInt64.valueOf(2),
  //            UInt64.valueOf(4));
  //
  //    assertThat(rootsBySlot).containsExactlyEntriesOf(getRootsForBlocks(5, 7));
  //  }
  //
  //  @Test
  //  void getAncestors_shouldGetSequenceOfRootsWhenEndIsAfterChainHead() {
  //    chainBuilder.generateBlocksUpToSlot(10);
  //
  //    final NavigableMap<UInt64, Bytes32> rootsBySlot =
  //        forkChoiceUtil.getAncestors(
  //            forkChoiceStrategy,
  //            chainBuilder.getLatestBlockAndState().getRoot(),
  //            UInt64.valueOf(6),
  //            UInt64.valueOf(2),
  //            UInt64.valueOf(20));
  //
  //    assertThat(rootsBySlot).containsExactlyEntriesOf(getRootsForBlocks(6, 8, 10));
  //  }
  //
  //  @Test
  //  void getAncestors_shouldNotIncludeEntryForEmptySlots() {
  //    chainBuilder.generateBlockAtSlot(3);
  //    chainBuilder.generateBlockAtSlot(5);
  //
  //    final NavigableMap<UInt64, Bytes32> rootsBySlot =
  //        forkChoiceUtil.getAncestors(
  //            forkChoiceStrategy,
  //            chainBuilder.getLatestBlockAndState().getRoot(),
  //            UInt64.ZERO,
  //            UInt64.ONE,
  //            UInt64.valueOf(10));
  //    assertThat(rootsBySlot).containsExactlyEntriesOf(getRootsForBlocks(0, 3, 5));
  //  }
  //
  //  @Test
  //  void getAncestorsOnFork_shouldIncludeHeadBlockAndExcludeStartSlot() {
  //    chainBuilder.generateBlocksUpToSlot(10);
  //
  //    final NavigableMap<UInt64, Bytes32> ancestorsOnFork =
  //        forkChoiceUtil.getAncestorsOnFork(
  //            forkChoiceStrategy, chainBuilder.getLatestBlockAndState().getRoot(),
  // UInt64.valueOf(5));
  //    assertThat(ancestorsOnFork).doesNotContainKey(UInt64.valueOf(5));
  //  }
  //
  //  @Test
  //  void getAncestorsOnFork_shouldNotIncludeHeadBlockWhenItIsAtStartSlot() {
  //    chainBuilder.generateBlocksUpToSlot(3);
  //
  //    final SignedBlockAndState headBlock = chainBuilder.getLatestBlockAndState();
  //    final NavigableMap<UInt64, Bytes32> ancestorsOnFork =
  //        forkChoiceUtil.getAncestorsOnFork(
  //            forkChoiceStrategy, headBlock.getRoot(), headBlock.getSlot());
  //    assertThat(ancestorsOnFork).isEmpty();
  //  }
  //
  //  @Test
  //  public void getCurrentSlot_shouldGetZeroAtGenesis() {
  //    assertThat(forkChoiceUtil.getCurrentSlot(GENESIS_TIME,
  // GENESIS_TIME)).isEqualTo(UInt64.ZERO);
  //  }
  //
  //  @Test
  //  public void getCurrentSlot_shouldGetNonZeroPastGenesis() {
  //
  //    assertThat(forkChoiceUtil.getCurrentSlot(slot50Time, GENESIS_TIME))
  //        .isEqualTo(UInt64.valueOf(50L));
  //  }
  //
  //  @Test
  //  public void getCurrentSlot_shouldGetZeroPriorToGenesis() {
  //    assertThat(forkChoiceUtil.getCurrentSlot(GENESIS_TIME.minus(1), GENESIS_TIME))
  //        .isEqualTo(UInt64.ZERO);
  //  }
  //
  //  @Test
  //  public void getSlotStartTime_shouldGetGenesisTimeForBlockZero() {
  //    assertThat(forkChoiceUtil.getSlotStartTime(UInt64.ZERO,
  // GENESIS_TIME)).isEqualTo(GENESIS_TIME);
  //  }
  //
  //  @Test
  //  public void getSlotStartTime_shouldGetCorrectTimePastGenesis() {
  //    assertThat(forkChoiceUtil.getSlotStartTime(UInt64.valueOf(50L), GENESIS_TIME))
  //        .isEqualTo(slot50Time);
  //  }
  //
  //  @Test
  //  void onTick_shouldExitImmediatelyWhenCurrentTimeIsBeforeGenesisTime() {
  //    final MutableStore store = mock(MutableStore.class);
  //    when(store.getGenesisTime()).thenReturn(UInt64.valueOf(3000));
  //    when(store.getTime()).thenReturn(UInt64.ZERO);
  //    forkChoiceUtil.onTick(store, UInt64.valueOf(2000));
  //
  //    verify(store, never()).setTime(any());
  //  }
  //
  //  @Test
  //  void onTick_shouldExitImmediatelyWhenCurrentTimeIsBeforeStoreTime() {
  //    final MutableStore store = mock(MutableStore.class);
  //    when(store.getGenesisTime()).thenReturn(UInt64.valueOf(3000));
  //    when(store.getTime()).thenReturn(UInt64.valueOf(5000));
  //    forkChoiceUtil.onTick(store, UInt64.valueOf(4000));
  //
  //    verify(store, never()).setTime(any());
  //  }
  //
  //  private Map<UInt64, Bytes32> getRootsForBlocks(final int... blockNumbers) {
  //    return IntStream.of(blockNumbers)
  //        .mapToObj(chainBuilder::getBlockAtSlot)
  //        .collect(Collectors.toMap(SignedBeaconBlock::getSlot, SignedBeaconBlock::getRoot));
  //  }
  //
  //  private static class ChainBuilderForkChoiceStrategy implements ReadOnlyForkChoiceStrategy {
  //    private final ChainBuilder chainBuilder;
  //    private UInt64 prunePriorToSlot = UInt64.ZERO;
  //
  //    private ChainBuilderForkChoiceStrategy(final ChainBuilder chainBuilder) {
  //      this.chainBuilder = chainBuilder;
  //    }
  //
  //    /**
  //     * Prune available block prior to the given slot
  //     *
  //     * @param slot
  //     */
  //    public void prune(final UInt64 slot) {
  //      this.prunePriorToSlot = slot;
  //    }
  //
  //    @Override
  //    public Optional<UInt64> blockSlot(final Bytes32 blockRoot) {
  //      return getBlock(blockRoot).map(SignedBeaconBlock::getSlot);
  //    }
  //
  //    @Override
  //    public Optional<Bytes32> blockParentRoot(final Bytes32 blockRoot) {
  //      return getBlock(blockRoot).map(SignedBeaconBlock::getParentRoot);
  //    }
  //
  //    @Override
  //    public Optional<Bytes32> getAncestor(final Bytes32 blockRoot, final UInt64 slot) {
  //      if (getBlock(blockRoot).isEmpty()) {
  //        return Optional.empty();
  //      }
  //      return getBlock(slot).map(SignedBeaconBlock::getRoot);
  //    }
  //
  //    @Override
  //    public Map<Bytes32, UInt64> getChainHeads() {
  //      final SignedBlockAndState latestBlock = chainBuilder.getLatestBlockAndState();
  //      if (latestBlock == null) {
  //        return Collections.emptyMap();
  //      }
  //      return Map.of(latestBlock.getRoot(), latestBlock.getSlot());
  //    }
  //
  //    @Override
  //    public boolean contains(final Bytes32 blockRoot) {
  //      return getBlock(blockRoot).isPresent();
  //    }
  //
  //    private Optional<SignedBeaconBlock> getBlock(final Bytes32 root) {
  //      return chainBuilder
  //          .getBlock(root)
  //          .filter(b -> b.getSlot().isGreaterThanOrEqualTo(prunePriorToSlot));
  //    }
  //
  //    private Optional<SignedBeaconBlock> getBlock(final UInt64 slot) {
  //      return Optional.ofNullable(chainBuilder.getBlockAtSlot(slot))
  //          .filter(b -> b.getSlot().isGreaterThanOrEqualTo(prunePriorToSlot));
  //    }
  //  }
}
