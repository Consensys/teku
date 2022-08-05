/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.storage.server.kvstore;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.spec.config.SpecConfig.GENESIS_SLOT;
import static tech.pegasys.teku.storage.store.StoreAssertions.assertStoresMatch;

import com.google.common.collect.Streams;
import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import com.google.errorprone.annotations.MustBeClosed;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import tech.pegasys.teku.bls.BLSKeyGenerator;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.dataproviders.lookup.BlockProvider;
import tech.pegasys.teku.dataproviders.lookup.StateAndBlockSummaryProvider;
import tech.pegasys.teku.ethereum.pow.api.MinGenesisTimeBlockEvent;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.SlotAndExecutionPayload;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.executionlayer.PayloadStatus;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.generator.ChainBuilder.BlockOptions;
import tech.pegasys.teku.spec.generator.ChainProperties;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.api.OnDiskStoreData;
import tech.pegasys.teku.storage.api.WeakSubjectivityUpdate;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.server.Database;
import tech.pegasys.teku.storage.server.DatabaseContext;
import tech.pegasys.teku.storage.server.ShuttingDownException;
import tech.pegasys.teku.storage.server.StateStorageMode;
import tech.pegasys.teku.storage.server.TestDatabaseContext;
import tech.pegasys.teku.storage.server.kvstore.dataaccess.KvStoreCombinedDaoCommon;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.storage.store.StoreAssertions;
import tech.pegasys.teku.storage.store.StoreBuilder;
import tech.pegasys.teku.storage.store.StoreConfig;
import tech.pegasys.teku.storage.store.UpdatableStore;
import tech.pegasys.teku.storage.store.UpdatableStore.StoreTransaction;

@TestDatabaseContext
@Execution(ExecutionMode.CONCURRENT)
public class DatabaseTest {

  private static final List<BLSKeyPair> VALIDATOR_KEYS = BLSKeyGenerator.generateKeyPairs(3);

  protected final Spec spec = TestSpecFactory.createMinimalBellatrix();
  final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final ChainBuilder chainBuilder = ChainBuilder.create(spec, VALIDATOR_KEYS);
  private final ChainProperties chainProperties = new ChainProperties(spec);
  private final List<File> tmpDirectories = new ArrayList<>();
  private final UInt64 genesisTime = UInt64.valueOf(100);
  private AnchorPoint genesisAnchor;
  private SignedBlockAndState genesisBlockAndState;
  private SignedBlockAndState checkpoint1BlockAndState;
  private SignedBlockAndState checkpoint2BlockAndState;
  private SignedBlockAndState checkpoint3BlockAndState;
  private Checkpoint genesisCheckpoint;
  private Checkpoint checkpoint1;
  private Checkpoint checkpoint2;
  private Checkpoint checkpoint3;
  private StateStorageMode storageMode;
  private StorageSystem storageSystem;
  private Database database;
  private RecentChainData recentChainData;
  private UpdatableStore store;
  private final List<StorageSystem> storageSystems = new ArrayList<>();

  @BeforeEach
  public void setup() throws IOException {
    genesisBlockAndState = chainBuilder.generateGenesis(genesisTime, true);
    genesisCheckpoint = getCheckpointForBlock(genesisBlockAndState.getBlock());
    genesisAnchor = AnchorPoint.fromGenesisState(spec, genesisBlockAndState.getState());
  }

  private void initialize(final DatabaseContext context, final StateStorageMode storageMode)
      throws IOException {
    createStorageSystem(context, storageMode, StoreConfig.createDefault(), false);

    // Initialize genesis store
    initGenesis();
  }

  private void initialize(final DatabaseContext context) throws IOException {
    initialize(context, StateStorageMode.ARCHIVE);
  }

  @AfterEach
  public void tearDown() throws Exception {
    for (StorageSystem storageSystem : storageSystems) {
      storageSystem.close();
    }
    for (File tmpDirectory : tmpDirectories) {
      MoreFiles.deleteRecursively(tmpDirectory.toPath(), RecursiveDeleteOption.ALLOW_INSECURE);
    }
    tmpDirectories.clear();
  }

  private void restartStorage() {
    final StorageSystem storage = storageSystem.restarted(storageMode);
    setDefaultStorage(storage);
  }

  @TestTemplate
  public void createMemoryStoreFromEmptyDatabase(final DatabaseContext context) throws IOException {
    createStorageSystem(context, StateStorageMode.ARCHIVE, StoreConfig.createDefault(), false);
    assertThat(database.createMemoryStore()).isEmpty();
  }

  @TestTemplate
  public void shouldRecreateOriginalGenesisStore(final DatabaseContext context) throws IOException {
    initialize(context);
    final UpdatableStore memoryStore = recreateStore();
    assertStoresMatch(memoryStore, store);
  }

  @TestTemplate
  public void updateWeakSubjectivityState_setValue(final DatabaseContext context)
      throws IOException {
    initialize(context);
    final Checkpoint checkpoint = dataStructureUtil.randomCheckpoint();
    assertThat(database.getWeakSubjectivityState().getCheckpoint()).isEmpty();

    final WeakSubjectivityUpdate update =
        WeakSubjectivityUpdate.setWeakSubjectivityCheckpoint(checkpoint);
    database.updateWeakSubjectivityState(update);

    assertThat(database.getWeakSubjectivityState().getCheckpoint()).contains(checkpoint);
  }

  @TestTemplate
  public void updateWeakSubjectivityState_clearValue(final DatabaseContext context)
      throws IOException {
    initialize(context);
    final Checkpoint checkpoint = dataStructureUtil.randomCheckpoint();

    // Set an initial value
    final WeakSubjectivityUpdate initUpdate =
        WeakSubjectivityUpdate.setWeakSubjectivityCheckpoint(checkpoint);
    database.updateWeakSubjectivityState(initUpdate);
    assertThat(database.getWeakSubjectivityState().getCheckpoint()).contains(checkpoint);

    // Clear the checkpoint
    final WeakSubjectivityUpdate update = WeakSubjectivityUpdate.clearWeakSubjectivityCheckpoint();
    database.updateWeakSubjectivityState(update);

    assertThat(database.getWeakSubjectivityState().getCheckpoint()).isEmpty();
  }

  @TestTemplate
  public void shouldGetHotBlockByRoot(final DatabaseContext context) throws IOException {
    initialize(context);
    final StoreTransaction transaction = recentChainData.startStoreTransaction();
    final SignedBlockAndState block1 = chainBuilder.generateBlockAtSlot(1);
    final SignedBlockAndState block2 = chainBuilder.generateBlockAtSlot(2);

    transaction.putBlockAndState(block1, spec.calculateBlockCheckpoints(block1.getState()));
    transaction.putBlockAndState(block2, spec.calculateBlockCheckpoints(block2.getState()));

    commit(transaction);

    assertThat(database.getSignedBlock(block1.getRoot())).contains(block1.getBlock());
    assertThat(database.getSignedBlock(block2.getRoot())).contains(block2.getBlock());
  }

  private void commit(final StoreTransaction transaction) {
    assertThat(transaction.commit()).isCompleted();
  }

  @TestTemplate
  public void shouldPruneHotBlocksAddedOverMultipleSessions(final DatabaseContext context)
      throws IOException {
    initialize(context);
    final UInt64 targetSlot = UInt64.valueOf(10);

    chainBuilder.generateBlocksUpToSlot(targetSlot.minus(UInt64.ONE));
    final ChainBuilder forkA = chainBuilder.fork();
    final ChainBuilder forkB = chainBuilder.fork();

    // Add base blocks
    addBlocks(chainBuilder.streamBlocksAndStates().collect(toList()));

    // Set target slot at which to create duplicate blocks
    // and generate block options to make each block unique
    final List<BlockOptions> blockOptions =
        chainBuilder
            .streamValidAttestationsForBlockAtSlot(targetSlot)
            .map(attestation -> BlockOptions.create().addAttestation(attestation))
            .limit(2)
            .collect(toList());

    // Create several different blocks at the same slot
    final SignedBlockAndState blockA = forkA.generateBlockAtSlot(targetSlot, blockOptions.get(0));
    final SignedBlockAndState blockB = forkB.generateBlockAtSlot(targetSlot, blockOptions.get(1));
    final SignedBlockAndState blockC = chainBuilder.generateBlockAtSlot(10);
    final Set<Bytes32> block10Roots = Set.of(blockA.getRoot(), blockB.getRoot(), blockC.getRoot());
    // Sanity check
    assertThat(block10Roots.size()).isEqualTo(3);

    // Add blocks at same height sequentially
    add(List.of(blockA));
    add(List.of(blockB));
    add(List.of(blockC));

    // Verify all blocks are available
    assertThat(store.retrieveBlock(blockA.getRoot()))
        .isCompletedWithValue(Optional.of(blockA.getBlock().getMessage()));
    assertThat(store.retrieveBlock(blockB.getRoot()))
        .isCompletedWithValue(Optional.of(blockB.getBlock().getMessage()));
    assertThat(store.retrieveBlock(blockC.getRoot()))
        .isCompletedWithValue(Optional.of(blockC.getBlock().getMessage()));

    // Finalize subsequent block to prune blocks a, b, and c
    final SignedBlockAndState finalBlock = chainBuilder.generateNextBlock();
    add(List.of(finalBlock));
    final UInt64 finalEpoch = chainBuilder.getLatestEpoch().plus(ONE);
    final SignedBlockAndState finalizedBlock =
        chainBuilder.getLatestBlockAndStateAtEpochBoundary(finalEpoch);
    justifyAndFinalizeEpoch(finalEpoch, finalizedBlock);

    // Check pruning result
    final Set<Bytes32> rootsToPrune = new HashSet<>(block10Roots);
    rootsToPrune.add(genesisBlockAndState.getRoot());
    // Check that all blocks at slot 10 were pruned
    assertRecentDataWasPruned(store, rootsToPrune);
  }

  @TestTemplate
  public void shouldPruneHotBlocksInCurrentTransactionFromChainThatIsInvalided(
      final DatabaseContext context) throws IOException {
    initialize(context);
    final UInt64 commonAncestorSlot = UInt64.valueOf(5);

    chainBuilder.generateBlocksUpToSlot(commonAncestorSlot);
    final ChainBuilder forkA = chainBuilder;
    final ChainBuilder forkB = chainBuilder.fork();

    // Add base blocks
    addBlocks(chainBuilder.streamBlocksAndStates().collect(toList()));

    // Forks diverge - generate block options to make each block unique
    final UInt64 divergingSlot = commonAncestorSlot.plus(1);
    final List<BlockOptions> blockOptions =
        chainBuilder
            .streamValidAttestationsForBlockAtSlot(divergingSlot)
            .map(attestation -> BlockOptions.create().addAttestation(attestation))
            .limit(2)
            .collect(toList());

    // Create several different blocks at the same slot
    final SignedBlockAndState blockA =
        forkA.generateBlockAtSlot(divergingSlot, blockOptions.get(0));
    final SignedBlockAndState blockB =
        forkB.generateBlockAtSlot(divergingSlot, blockOptions.get(1));

    // Add diverging blocks sequentially
    add(List.of(blockA));
    add(List.of(blockB));

    // Then build on both chains, into the next epoch
    final SignedBlockAndState blockA2 =
        forkA.generateBlockAtSlot(spec.slotsPerEpoch(ZERO) * 2L + 2);
    final SignedBlockAndState blockB2 =
        forkB.generateBlockAtSlot(spec.slotsPerEpoch(ZERO) * 2L + 2);

    // Add blocks while finalizing blockA at the same time
    StoreTransaction tx = recentChainData.startStoreTransaction();
    tx.putBlockAndState(blockA2, spec.calculateBlockCheckpoints(blockA2.getState()));
    tx.putBlockAndState(blockB2, spec.calculateBlockCheckpoints(blockB2.getState()));
    justifyAndFinalizeEpoch(UInt64.ONE, blockA, tx);
    assertThat(tx.commit()).isCompleted();

    // Verify all fork B blocks were pruned
    assertThatSafeFuture(store.retrieveBlock(blockB.getRoot())).isCompletedWithEmptyOptional();
    assertThatSafeFuture(store.retrieveBlock(blockB2.getRoot())).isCompletedWithEmptyOptional();

    // And fork A should be available.
    assertThat(store.retrieveSignedBlock(blockA.getRoot()))
        .isCompletedWithValue(Optional.of(blockA.getBlock()));
    assertThat(store.retrieveSignedBlock(blockA2.getRoot()))
        .isCompletedWithValue(Optional.of(blockA2.getBlock()));
  }

  @TestTemplate
  public void getFinalizedState(final DatabaseContext context) throws IOException {
    initialize(context);
    generateCheckpoints();
    final Checkpoint finalizedCheckpoint = chainBuilder.getCurrentCheckpointForEpoch(UInt64.ONE);
    final SignedBlockAndState block2 =
        chainBuilder.getLatestBlockAndStateAtEpochBoundary(UInt64.ONE);
    final SignedBlockAndState block1 =
        chainBuilder.getBlockAndStateAtSlot(block2.getSlot().minus(UInt64.ONE));

    final List<SignedBlockAndState> allBlocks =
        chainBuilder.streamBlocksAndStates(0, block2.getSlot().longValue()).collect(toList());
    addBlocks(allBlocks);

    // Finalize block2
    final StoreTransaction transaction = recentChainData.startStoreTransaction();
    transaction.setFinalizedCheckpoint(finalizedCheckpoint, false);
    commit(transaction);

    assertThat(database.getLatestAvailableFinalizedState(block2.getSlot()))
        .contains(block2.getState());
    assertThat(database.getLatestAvailableFinalizedState(block1.getSlot()))
        .contains(block1.getState());
  }

  @TestTemplate
  public void shouldStoreSingleValueFields(final DatabaseContext context) throws IOException {
    initialize(context);
    generateCheckpoints();

    final List<SignedBlockAndState> allBlocks =
        chainBuilder
            .streamBlocksAndStates(0, checkpoint3BlockAndState.getSlot().longValue())
            .collect(toList());
    addBlocks(allBlocks);

    final StoreTransaction transaction = recentChainData.startStoreTransaction();
    transaction.setGenesisTime(UInt64.valueOf(3));
    transaction.setFinalizedCheckpoint(checkpoint1, false);
    transaction.setJustifiedCheckpoint(checkpoint2);
    transaction.setBestJustifiedCheckpoint(checkpoint3);

    commit(transaction);

    final UpdatableStore result = recreateStore();

    assertThat(result.getGenesisTime()).isEqualTo(transaction.getGenesisTime());
    assertThat(result.getFinalizedCheckpoint()).isEqualTo(transaction.getFinalizedCheckpoint());
    assertThat(result.getJustifiedCheckpoint()).isEqualTo(transaction.getJustifiedCheckpoint());
    assertThat(result.getBestJustifiedCheckpoint())
        .isEqualTo(transaction.getBestJustifiedCheckpoint());
  }

  @TestTemplate
  public void shouldStoreSingleValue_genesisTime(final DatabaseContext context) throws IOException {
    initialize(context);
    final UInt64 newGenesisTime = UInt64.valueOf(3);
    // Sanity check
    assertThat(store.getGenesisTime()).isNotEqualTo(newGenesisTime);

    final StoreTransaction transaction = recentChainData.startStoreTransaction();
    transaction.setGenesisTime(newGenesisTime);
    commit(transaction);

    final UpdatableStore result = recreateStore();
    assertThat(result.getGenesisTime()).isEqualTo(transaction.getGenesisTime());
  }

  @TestTemplate
  public void shouldStoreSingleValue_justifiedCheckpoint(final DatabaseContext context)
      throws IOException {
    initialize(context);
    generateCheckpoints();
    final Checkpoint newValue = checkpoint3;
    // Sanity check
    assertThat(store.getJustifiedCheckpoint()).isNotEqualTo(checkpoint3);

    final StoreTransaction transaction = recentChainData.startStoreTransaction();
    transaction.setJustifiedCheckpoint(newValue);
    commit(transaction);

    final UpdatableStore result = recreateStore();
    assertThat(result.getJustifiedCheckpoint()).isEqualTo(newValue);
  }

  @TestTemplate
  public void shouldStoreSingleValue_finalizedCheckpoint(final DatabaseContext context)
      throws IOException {
    initialize(context);
    generateCheckpoints();
    final List<SignedBlockAndState> allBlocks =
        chainBuilder
            .streamBlocksAndStates(0, checkpoint3BlockAndState.getSlot().longValue())
            .collect(toList());
    addBlocks(allBlocks);

    final Checkpoint newValue = checkpoint3;
    // Sanity check
    assertThat(store.getFinalizedCheckpoint()).isNotEqualTo(checkpoint3);

    justifyAndFinalizeEpoch(newValue.getEpoch(), checkpoint3BlockAndState);

    final UpdatableStore result = recreateStore();
    assertThat(result.getFinalizedCheckpoint()).isEqualTo(newValue);
  }

  @TestTemplate
  public void shouldStoreSingleValue_bestJustifiedCheckpoint(final DatabaseContext context)
      throws IOException {
    initialize(context);
    generateCheckpoints();
    final Checkpoint newValue = checkpoint3;
    // Sanity check
    assertThat(store.getBestJustifiedCheckpoint()).isNotEqualTo(checkpoint3);

    final StoreTransaction transaction = recentChainData.startStoreTransaction();
    transaction.setBestJustifiedCheckpoint(newValue);
    commit(transaction);

    final UpdatableStore result = recreateStore();
    assertThat(result.getBestJustifiedCheckpoint()).isEqualTo(newValue);
  }

  @TestTemplate
  public void shouldStoreSingleValue_singleBlockAndState(final DatabaseContext context)
      throws IOException {
    initialize(context);
    final SignedBlockAndState newBlock = chainBuilder.generateNextBlock();
    // Sanity check
    assertThatSafeFuture(store.retrieveBlock(newBlock.getRoot())).isCompletedWithEmptyOptional();

    final StoreTransaction transaction = recentChainData.startStoreTransaction();
    transaction.putBlockAndState(newBlock, spec.calculateBlockCheckpoints(newBlock.getState()));
    commit(transaction);

    final UpdatableStore result = recreateStore();
    assertThat(result.retrieveSignedBlock(newBlock.getRoot()))
        .isCompletedWithValue(Optional.of(newBlock.getBlock()));
    assertThat(result.retrieveBlockState(newBlock.getRoot()))
        .isCompletedWithValue(Optional.of(newBlock.getState()));
  }

  @TestTemplate
  public void shouldLoadHotBlocksAndStatesIntoMemoryStore(final DatabaseContext context)
      throws IOException {
    initialize(context);
    final Bytes32 genesisRoot = genesisBlockAndState.getRoot();
    final StoreTransaction transaction = recentChainData.startStoreTransaction();

    final SignedBlockAndState blockAndState1 = chainBuilder.generateBlockAtSlot(1);
    final SignedBlockAndState blockAndState2 = chainBuilder.generateBlockAtSlot(2);

    transaction.putBlockAndState(
        blockAndState1, spec.calculateBlockCheckpoints(blockAndState1.getState()));
    transaction.putBlockAndState(
        blockAndState2, spec.calculateBlockCheckpoints(blockAndState2.getState()));

    commit(transaction);

    final UpdatableStore result = recreateStore();
    assertThat(result.retrieveSignedBlock(genesisRoot))
        .isCompletedWithValue(Optional.of(genesisBlockAndState.getBlock()));
    assertThat(result.retrieveSignedBlock(blockAndState1.getRoot()))
        .isCompletedWithValue(Optional.of(blockAndState1.getBlock()));
    assertThat(result.retrieveSignedBlock(blockAndState2.getRoot()))
        .isCompletedWithValue(Optional.of(blockAndState2.getBlock()));
    assertThat(result.retrieveBlockState(blockAndState1.getRoot()))
        .isCompletedWithValue(Optional.of(blockAndState1.getState()));
    assertThat(result.retrieveBlockState(blockAndState2.getRoot()))
        .isCompletedWithValue(Optional.of(blockAndState2.getState()));
  }

  @TestTemplate
  public void shouldRemoveHotBlocksAndStatesOnceEpochIsFinalized(final DatabaseContext context)
      throws IOException {
    initialize(context);
    generateCheckpoints();
    final List<SignedBlockAndState> allBlocks =
        chainBuilder
            .streamBlocksAndStates(0, checkpoint2BlockAndState.getSlot().longValue())
            .collect(toList());
    addBlocks(allBlocks);

    // Finalize block
    justifyAndFinalizeEpoch(checkpoint1.getEpoch(), checkpoint1BlockAndState);

    final List<SignedBlockAndState> historicalBlocks =
        chainBuilder
            .streamBlocksAndStates(0, checkpoint1BlockAndState.getSlot().longValue())
            .collect(toList());
    historicalBlocks.remove(checkpoint1BlockAndState);
    final List<SignedBlockAndState> hotBlocks =
        chainBuilder
            .streamBlocksAndStates(
                checkpoint1BlockAndState.getSlot(), checkpoint2BlockAndState.getSlot())
            .collect(toList());

    final UpdatableStore result = recreateStore();
    // Historical blocks should not be in the new store
    for (SignedBlockAndState historicalBlock : historicalBlocks) {
      assertThatSafeFuture(result.retrieveSignedBlock(historicalBlock.getRoot()))
          .isCompletedWithEmptyOptional();
      assertThatSafeFuture(result.retrieveBlockState(historicalBlock.getRoot()))
          .isCompletedWithEmptyOptional();
    }

    // Hot blocks should be available in the new store
    for (SignedBlockAndState hotBlock : hotBlocks) {
      assertThat(result.retrieveSignedBlock(hotBlock.getRoot()))
          .isCompletedWithValue(Optional.of(hotBlock.getBlock()));
      assertThat(result.retrieveBlockState(hotBlock.getRoot()))
          .isCompletedWithValue(Optional.of(hotBlock.getState()));
    }

    final Set<Bytes32> hotBlockRoots =
        hotBlocks.stream().map(SignedBlockAndState::getRoot).collect(Collectors.toSet());
    assertThat(result.getOrderedBlockRoots()).containsExactlyInAnyOrderElementsOf(hotBlockRoots);
  }

  @TestTemplate
  public void shouldRecordAndRetrieveGenesisInformation(final DatabaseContext context)
      throws IOException {
    initialize(context);
    final MinGenesisTimeBlockEvent event =
        new MinGenesisTimeBlockEvent(
            dataStructureUtil.randomUInt64(),
            dataStructureUtil.randomUInt64(),
            dataStructureUtil.randomBytes32());
    database.addMinGenesisTimeBlock(event);

    final Optional<MinGenesisTimeBlockEvent> fetch = database.getMinGenesisTimeBlock();
    assertThat(fetch).contains(event);
  }

  @TestTemplate
  public void handleFinalizationWhenCacheLimitsExceeded(final DatabaseContext context)
      throws IOException {
    initialize(context);

    final int startSlot = genesisBlockAndState.getSlot().intValue();
    final int minFinalSlot = startSlot + StoreConfig.DEFAULT_STATE_CACHE_SIZE + 10;
    final UInt64 finalizedEpoch = chainProperties.computeBestEpochFinalizableAtSlot(minFinalSlot);
    final UInt64 finalizedSlot = spec.computeStartSlotAtEpoch(finalizedEpoch);

    chainBuilder.generateBlocksUpToSlot(finalizedSlot);
    final Checkpoint finalizedCheckpoint =
        chainBuilder.getCurrentCheckpointForEpoch(finalizedEpoch);

    // Save all blocks and states in a single transaction
    final List<SignedBlockAndState> newBlocks =
        chainBuilder.streamBlocksAndStates(startSlot).collect(toList());
    add(newBlocks);
    // Then finalize
    final StoreTransaction tx = recentChainData.startStoreTransaction();
    tx.setFinalizedCheckpoint(finalizedCheckpoint, false);
    assertThat(tx.commit()).isCompleted();

    // All finalized blocks and states should be available
    final List<SignedBeaconBlock> expectedFinalizedBlocks =
        newBlocks.stream().map(SignedBlockAndState::getBlock).collect(toList());
    final Map<Bytes32, BeaconState> expectedFinalizedStates =
        newBlocks.stream()
            // Hot state is recorded for the first block of each epoch and we only use the available
            // states, so ensure that at least those are available (some others will be available
            // because they were in cache)
            .filter(
                blockAndState ->
                    blockAndState
                        .getSlot()
                        .equals(spec.computeStartSlotAtEpoch(blockAndState.getSlot())))
            .collect(Collectors.toMap(SignedBlockAndState::getRoot, SignedBlockAndState::getState));
    assertBlocksFinalized(expectedFinalizedBlocks);
    assertBlocksAvailable(expectedFinalizedBlocks);
    assertFinalizedStatesAvailable(expectedFinalizedStates);
  }

  @TestTemplate
  public void shouldRecordOptimisticTransitionExecutionPayloadWhenFinalized_singleTransaction(
      final DatabaseContext context) throws IOException {
    final SignedBlockAndState transitionBlock =
        generateChainWithFinalizableTransitionBlock(context);
    final List<SignedBlockAndState> newBlocks =
        chainBuilder
            .streamBlocksAndStates(genesisBlockAndState.getSlot().intValue())
            .collect(toList());
    // Save all blocks and states in a single transaction
    add(newBlocks);

    // Then finalize
    final Checkpoint finalizedCheckpoint =
        chainBuilder.getCurrentCheckpointForEpoch(chainBuilder.getLatestEpoch());
    final StoreTransaction tx = recentChainData.startStoreTransaction();
    tx.setFinalizedCheckpoint(finalizedCheckpoint, false);
    assertThat(tx.commit()).isCompleted();

    final Optional<SlotAndExecutionPayload> transitionPayload =
        SlotAndExecutionPayload.fromBlock(transitionBlock.getBlock());
    assertThat(transitionPayload).isPresent();
    assertThat(transitionPayload.get().getExecutionPayload().isDefault()).isFalse();
    assertThat(recentChainData.getStore().getFinalizedOptimisticTransitionPayload())
        .isEqualTo(transitionPayload);
  }

  @TestTemplate
  public void shouldNotRecordTransitionExecutionPayloadWhenNotOptimistic(
      final DatabaseContext context) throws IOException {
    final SignedBlockAndState transitionBlock =
        generateChainWithFinalizableTransitionBlock(context);
    final List<SignedBlockAndState> newBlocks =
        chainBuilder
            .streamBlocksAndStates(genesisBlockAndState.getSlot().intValue())
            .collect(toList());

    // Save all blocks and states in a single transaction
    add(newBlocks);
    recentChainData
        .getUpdatableForkChoiceStrategy()
        .orElseThrow()
        .onExecutionPayloadResult(transitionBlock.getRoot(), PayloadStatus.VALID, true);

    // Then finalize
    final Checkpoint finalizedCheckpoint =
        chainBuilder.getCurrentCheckpointForEpoch(chainBuilder.getLatestEpoch());
    final StoreTransaction tx = recentChainData.startStoreTransaction();
    tx.setFinalizedCheckpoint(finalizedCheckpoint, false);
    assertThat(tx.commit()).isCompleted();

    final Optional<ExecutionPayload> transitionPayload =
        transitionBlock.getBlock().getMessage().getBody().getOptionalExecutionPayload();
    assertThat(transitionPayload).isPresent();
    assertThat(transitionPayload.get().isDefault()).isFalse();
    assertThat(recentChainData.getStore().getFinalizedOptimisticTransitionPayload()).isEmpty();
  }

  @TestTemplate
  public void shouldRecordOptimisticTransitionExecutionPayloadWhenFinalized_multiTransaction(
      final DatabaseContext context) throws IOException {
    final SignedBlockAndState transitionBlock =
        generateChainWithFinalizableTransitionBlock(context);
    final List<SignedBlockAndState> newBlocks =
        chainBuilder
            .streamBlocksAndStates(genesisBlockAndState.getSlot().intValue())
            .collect(toList());
    // Save all blocks and states in separate transactions
    for (SignedBlockAndState newBlock : newBlocks) {
      add(List.of(newBlock));
    }
    assertThat(recentChainData.getStore().getFinalizedOptimisticTransitionPayload()).isEmpty();

    // Then finalize
    final Checkpoint finalizedCheckpoint =
        chainBuilder.getCurrentCheckpointForEpoch(chainBuilder.getLatestEpoch());
    final StoreTransaction tx = recentChainData.startStoreTransaction();
    tx.setFinalizedCheckpoint(finalizedCheckpoint, false);
    assertThat(tx.commit()).isCompleted();

    final Optional<SlotAndExecutionPayload> transitionPayload =
        SlotAndExecutionPayload.fromBlock(transitionBlock.getBlock());
    assertThat(transitionPayload).isPresent();
    assertThat(transitionPayload.get().getExecutionPayload().isDefault()).isFalse();
    assertThat(recentChainData.getStore().getFinalizedOptimisticTransitionPayload())
        .isEqualTo(transitionPayload);
  }

  @TestTemplate
  public void shouldPersistOptimisticTransitionExecutionPayload(final DatabaseContext context)
      throws IOException {
    final SignedBlockAndState transitionBlock =
        generateChainWithFinalizableTransitionBlock(context);
    final List<SignedBlockAndState> newBlocks =
        chainBuilder
            .streamBlocksAndStates(genesisBlockAndState.getSlot().intValue())
            .collect(toList());
    // Save all blocks and states in a single transaction
    add(newBlocks);
    // Then finalize
    final Checkpoint finalizedCheckpoint =
        chainBuilder.getCurrentCheckpointForEpoch(chainBuilder.getLatestEpoch());
    final StoreTransaction tx = recentChainData.startStoreTransaction();
    tx.setFinalizedCheckpoint(finalizedCheckpoint, false);
    assertThat(tx.commit()).isCompleted();

    final Optional<SlotAndExecutionPayload> transitionPayload =
        SlotAndExecutionPayload.fromBlock(transitionBlock.getBlock());
    assertThat(transitionPayload).isPresent();
    assertThat(recentChainData.getStore().getFinalizedOptimisticTransitionPayload())
        .isEqualTo(transitionPayload);

    restartStorage();
    assertThat(recentChainData.getStore().getFinalizedOptimisticTransitionPayload())
        .isEqualTo(transitionPayload);
  }

  @TestTemplate
  public void shouldClearOptimisticTransitionExecutionPayload(final DatabaseContext context)
      throws IOException {
    // Record optimistic transition execution payload.
    final SignedBlockAndState transitionBlock =
        generateChainWithFinalizableTransitionBlock(context);
    final List<SignedBlockAndState> newBlocks =
        chainBuilder
            .streamBlocksAndStates(genesisBlockAndState.getSlot().intValue())
            .collect(toList());
    add(newBlocks);
    final Checkpoint finalizedCheckpoint =
        chainBuilder.getCurrentCheckpointForEpoch(chainBuilder.getLatestEpoch());
    final StoreTransaction tx = recentChainData.startStoreTransaction();
    tx.setFinalizedCheckpoint(finalizedCheckpoint, false);
    assertThat(tx.commit()).isCompleted();
    final Optional<SlotAndExecutionPayload> transitionPayload =
        SlotAndExecutionPayload.fromBlock(transitionBlock.getBlock());
    assertThat(transitionPayload).isPresent();
    assertThat(recentChainData.getStore().getFinalizedOptimisticTransitionPayload())
        .isEqualTo(transitionPayload);

    // Clear optimistic transition payload
    final StoreTransaction clearTx = recentChainData.startStoreTransaction();
    clearTx.removeFinalizedOptimisticTransitionPayload();
    assertThat(clearTx.commit()).isCompleted();

    assertThat(store.getFinalizedOptimisticTransitionPayload()).isEmpty();

    restartStorage();
    assertThat(store.getFinalizedOptimisticTransitionPayload()).isEmpty();
  }

  @TestTemplate
  public void shouldNotRemoveOptimisticFinalizedExceptionPayloadWhenFinalizedNextUpdated(
      final DatabaseContext context) throws IOException {
    final SignedBlockAndState transitionBlock =
        generateChainWithFinalizableTransitionBlock(context);
    final List<SignedBlockAndState> newBlocks =
        chainBuilder
            .streamBlocksAndStates(genesisBlockAndState.getSlot().intValue())
            .collect(toList());
    // Save all blocks and states in a single transaction
    add(newBlocks);

    // Then finalize
    final Checkpoint finalizedCheckpoint =
        chainBuilder.getCurrentCheckpointForEpoch(chainBuilder.getLatestEpoch());
    final StoreTransaction tx = recentChainData.startStoreTransaction();
    tx.setFinalizedCheckpoint(finalizedCheckpoint, false);
    assertThat(tx.commit()).isCompleted();

    // Finalize the next epoch
    final List<SignedBlockAndState> laterBlocks =
        chainBuilder.generateBlocksUpToSlot(
            chainBuilder.getLatestSlot().plus(spec.getSlotsPerEpoch(chainBuilder.getLatestSlot())));
    add(laterBlocks);
    final Checkpoint finalizedCheckpoint2 =
        chainBuilder.getCurrentCheckpointForEpoch(chainBuilder.getLatestEpoch());
    final StoreTransaction tx2 = recentChainData.startStoreTransaction();
    tx2.setFinalizedCheckpoint(finalizedCheckpoint2, false);
    assertThat(tx2.commit()).isCompleted();

    final Optional<SlotAndExecutionPayload> transitionPayload =
        SlotAndExecutionPayload.fromBlock(transitionBlock.getBlock());
    assertThat(transitionPayload).isPresent();
    assertThat(transitionPayload.get().getExecutionPayload().isDefault()).isFalse();
    assertThat(recentChainData.getStore().getFinalizedOptimisticTransitionPayload())
        .isEqualTo(transitionPayload);
  }

  /**
   * Generates a chain in chainBuilder that can be finalized at the current epoch, including a merge
   * transition block.
   *
   * @return the merge transition block
   */
  private SignedBlockAndState generateChainWithFinalizableTransitionBlock(
      final DatabaseContext context) throws IOException {
    initialize(context, StateStorageMode.PRUNE);

    final int startSlot = genesisBlockAndState.getSlot().intValue();
    final int minFinalSlot = startSlot + StoreConfig.DEFAULT_STATE_CACHE_SIZE + 10;
    final UInt64 finalizedEpoch = chainProperties.computeBestEpochFinalizableAtSlot(minFinalSlot);
    final UInt64 finalizedSlot = spec.computeStartSlotAtEpoch(finalizedEpoch);

    chainBuilder.generateBlocksUpToSlot(finalizedSlot.minus(5));
    final SignedBlockAndState transitionBlock =
        chainBuilder.generateBlockAtSlot(
            finalizedSlot.minus(4),
            BlockOptions.create()
                .setTransactions(Bytes32.ZERO)
                .setTerminalBlockHash(Bytes32.fromHexString("0x1234")));
    chainBuilder.generateBlocksUpToSlot(finalizedSlot);
    return transitionBlock;
  }

  @TestTemplate
  public void shouldRecordFinalizedBlocksAndStates_pruneMode(final DatabaseContext context)
      throws IOException {
    testShouldRecordFinalizedBlocksAndStates(context, StateStorageMode.PRUNE, false);
  }

  @TestTemplate
  public void shouldRecordFinalizedBlocksAndStates_archiveMode(final DatabaseContext context)
      throws IOException {
    testShouldRecordFinalizedBlocksAndStates(context, StateStorageMode.ARCHIVE, false);
  }

  @TestTemplate
  public void testShouldRecordFinalizedBlocksAndStatesInBatchUpdate(final DatabaseContext context)
      throws IOException {
    testShouldRecordFinalizedBlocksAndStates(context, StateStorageMode.ARCHIVE, true);
  }

  @TestTemplate
  public void slotAndBlock_shouldStoreAndRetrieve(final DatabaseContext context)
      throws IOException {
    initialize(context);
    final Bytes32 stateRoot = dataStructureUtil.randomBytes32();
    final SlotAndBlockRoot slotAndBlockRoot =
        new SlotAndBlockRoot(dataStructureUtil.randomUInt64(), dataStructureUtil.randomBytes32());

    database.addHotStateRoots(Map.of(stateRoot, slotAndBlockRoot));

    final Optional<SlotAndBlockRoot> fromStorage =
        database.getSlotAndBlockRootFromStateRoot(stateRoot);

    assertThat(fromStorage.isPresent()).isTrue();
    assertThat(fromStorage.get()).isEqualTo(slotAndBlockRoot);
  }

  @TestTemplate
  public void getEarliestAvailableBlockSlot_withMissingFinalizedBlocks(
      final DatabaseContext context) throws IOException {
    createStorageSystem(context, StateStorageMode.PRUNE, StoreConfig.createDefault(), false);
    // Set up database from an anchor point
    final UInt64 anchorEpoch = UInt64.valueOf(10);
    final SignedBlockAndState anchorBlockAndState =
        chainBuilder.generateBlockAtSlot(spec.computeStartSlotAtEpoch(anchorEpoch));
    final AnchorPoint anchor =
        AnchorPoint.create(
            spec, new Checkpoint(anchorEpoch, anchorBlockAndState.getRoot()), anchorBlockAndState);
    initFromAnchor(anchor);

    // Add some blocks
    addBlocks(chainBuilder.generateNextBlock(), chainBuilder.generateNextBlock());
    // And finalize them
    justifyAndFinalizeEpoch(anchorEpoch.plus(1), chainBuilder.getLatestBlockAndState());

    assertThat(database.getEarliestAvailableBlockSlot()).contains(anchorBlockAndState.getSlot());
  }

  @TestTemplate
  public void getEarliestAvailableBlockSlot_noBlocksMissing(final DatabaseContext context)
      throws IOException {
    initialize(context);
    // Add some blocks
    addBlocks(chainBuilder.generateNextBlock(), chainBuilder.generateNextBlock());
    // And finalize them
    justifyAndFinalizeEpoch(UInt64.valueOf(1), chainBuilder.getLatestBlockAndState());

    assertThat(database.getEarliestAvailableBlockSlot()).contains(genesisBlockAndState.getSlot());
  }

  @TestTemplate
  public void slotAndBlock_shouldGetStateRootsBeforeSlot(final DatabaseContext context)
      throws IOException {
    initialize(context);
    final Bytes32 zeroStateRoot = insertRandomSlotAndBlock(0L, dataStructureUtil);
    final Bytes32 oneStateRoot = insertRandomSlotAndBlock(1L, dataStructureUtil);
    insertRandomSlotAndBlock(2L, dataStructureUtil);
    insertRandomSlotAndBlock(3L, dataStructureUtil);

    assertThat(database.getStateRootsBeforeSlot(UInt64.valueOf(2L)))
        .containsExactlyInAnyOrder(zeroStateRoot, oneStateRoot);
  }

  @TestTemplate
  public void slotAndBlock_shouldPurgeToSlot(final DatabaseContext context) throws IOException {
    initialize(context);
    insertRandomSlotAndBlock(0L, dataStructureUtil);
    insertRandomSlotAndBlock(1L, dataStructureUtil);
    final Bytes32 twoStateRoot = insertRandomSlotAndBlock(2L, dataStructureUtil);
    final Bytes32 threeStateRoot = insertRandomSlotAndBlock(3L, dataStructureUtil);

    database.pruneHotStateRoots(database.getStateRootsBeforeSlot(UInt64.valueOf(2L)));
    assertThat(database.getStateRootsBeforeSlot(UInt64.valueOf(10L)))
        .containsExactlyInAnyOrder(twoStateRoot, threeStateRoot);
  }

  @TestTemplate
  public void startupFromNonGenesisState_prune(final DatabaseContext context) throws IOException {
    testStartupFromNonGenesisState(context, StateStorageMode.PRUNE);
  }

  @TestTemplate
  public void startupFromNonGenesisState_archive(final DatabaseContext context) throws IOException {
    testStartupFromNonGenesisState(context, StateStorageMode.ARCHIVE);
  }

  @TestTemplate
  public void orphanedBlockStorageTest_withCanonicalBlocks(final DatabaseContext context)
      throws IOException {
    createStorageSystem(context, StateStorageMode.ARCHIVE, StoreConfig.createDefault(), true);
    final CreateForkChainResult forkChainResult = createForkChain(false);
    assertBlocksAvailable(
        forkChainResult
            .getForkChain()
            .streamBlocksAndStates(4, forkChainResult.getFirstHotBlockSlot().longValue())
            .map(SignedBlockAndState::getBlock)
            .collect(Collectors.toList()));
  }

  @TestTemplate
  public void orphanedBlockStorageTest_multiple(final DatabaseContext context) throws IOException {
    createStorageSystem(context, StateStorageMode.ARCHIVE, StoreConfig.createDefault(), true);
    final ChainBuilder primaryChain = ChainBuilder.create(spec, VALIDATOR_KEYS);
    primaryChain.generateGenesis(genesisTime, true);
    primaryChain.generateBlocksUpToSlot(3);
    final ChainBuilder forkChain = primaryChain.fork();
    // Primary chain's next block is at 5
    primaryChain.generateBlockAtSlot(5);
    final ChainBuilder secondFork = primaryChain.fork();

    // Primary chain's next block is at 7
    final SignedBlockAndState finalizedBlock = primaryChain.generateBlockAtSlot(7);
    final Checkpoint finalizedCheckpoint = getCheckpointForBlock(primaryChain.getBlockAtSlot(7));
    final UInt64 firstHotBlockSlot = finalizedCheckpoint.getEpochStartSlot(spec).plus(UInt64.ONE);
    primaryChain.generateBlockAtSlot(firstHotBlockSlot);
    // Fork chain's next block is at 6
    forkChain.generateBlockAtSlot(6);
    forkChain.generateBlockAtSlot(firstHotBlockSlot);
    secondFork.generateBlockAtSlot(6);
    secondFork.generateBlockAtSlot(firstHotBlockSlot);

    initGenesis();

    final Set<SignedBlockAndState> allBlocksAndStates =
        Streams.concat(
                primaryChain.streamBlocksAndStates(),
                forkChain.streamBlocksAndStates(),
                secondFork.streamBlocksAndStates())
            .collect(Collectors.toSet());

    // Finalize at block 7, making the fork blocks unavailable
    add(allBlocksAndStates);
    justifyAndFinalizeEpoch(finalizedCheckpoint.getEpoch(), finalizedBlock);

    assertThat(database.getNonCanonicalBlocksAtSlot(UInt64.valueOf(6)).size()).isEqualTo(2);
  }

  @TestTemplate
  public void orphanedBlockStorageTest_noCanonicalBlocks(final DatabaseContext context)
      throws IOException {
    createStorageSystem(context, StateStorageMode.ARCHIVE, StoreConfig.createDefault(), false);
    final CreateForkChainResult forkChainResult = createForkChain(false);
    assertBlocksUnavailable(
        forkChainResult
            .getForkChain()
            .streamBlocksAndStates(4, forkChainResult.getFirstHotBlockSlot().longValue())
            .map(SignedBlockAndState::getRoot)
            .collect(Collectors.toList()));
  }

  @TestTemplate
  public void shouldRecreateGenesisStateOnRestart_archiveMode(final DatabaseContext context)
      throws IOException {
    initialize(context, StateStorageMode.ARCHIVE);
    testShouldRecreateGenesisStateOnRestart();
  }

  @TestTemplate
  public void shouldRecreateGenesisStateOnRestart_pruneMode(final DatabaseContext context)
      throws IOException {
    initialize(context, StateStorageMode.PRUNE);
    testShouldRecreateGenesisStateOnRestart();
  }

  public void testShouldRecreateGenesisStateOnRestart() {
    // Shutdown and restart
    restartStorage();

    final UpdatableStore memoryStore = recreateStore();
    assertStoresMatch(memoryStore, store);
    assertThat(database.getEarliestAvailableBlockSlot()).contains(genesisBlockAndState.getSlot());
  }

  @TestTemplate
  public void shouldRecreateStoreOnRestart_withOffEpochBoundaryFinalizedBlock_archiveMode(
      final DatabaseContext context) throws IOException {
    initialize(context, StateStorageMode.ARCHIVE);
    testShouldRecreateStoreOnRestartWithOffEpochBoundaryFinalizedBlock();
  }

  @TestTemplate
  public void shouldRecreateStoreOnRestart_withOffEpochBoundaryFinalizedBlock_pruneMode(
      final DatabaseContext context) throws IOException {
    initialize(context, StateStorageMode.PRUNE);
    testShouldRecreateStoreOnRestartWithOffEpochBoundaryFinalizedBlock();
  }

  private void testShouldRecreateStoreOnRestartWithOffEpochBoundaryFinalizedBlock() {

    // Create finalized block at slot prior to epoch boundary
    final UInt64 finalizedEpoch = UInt64.valueOf(2);
    final UInt64 finalizedSlot = spec.computeStartSlotAtEpoch(finalizedEpoch).minus(UInt64.ONE);
    chainBuilder.generateBlocksUpToSlot(finalizedSlot);
    final SignedBlockAndState finalizedBlock = chainBuilder.getBlockAndStateAtSlot(finalizedSlot);
    final Checkpoint finalizedCheckpoint =
        chainBuilder.getCurrentCheckpointForEpoch(finalizedEpoch);

    // Add some more blocks
    final UInt64 firstHotBlockSlot = finalizedCheckpoint.getEpochStartSlot(spec).plus(UInt64.ONE);
    chainBuilder.generateBlockAtSlot(firstHotBlockSlot);
    chainBuilder.generateBlocksUpToSlot(firstHotBlockSlot.plus(10));

    // Save new blocks and finalized checkpoint
    final StoreTransaction tx = recentChainData.startStoreTransaction();
    chainBuilder.streamBlocksAndStates(1).forEach(b -> add(tx, List.of(b)));
    justifyAndFinalizeEpoch(finalizedCheckpoint.getEpoch(), finalizedBlock, tx);
    tx.commit().join();

    // Shutdown and restart
    restartStorage();

    final UpdatableStore memoryStore = recreateStore();
    assertStoresMatch(memoryStore, store);
  }

  @TestTemplate
  public void shouldPersistOnDisk_pruneMode(final DatabaseContext context) throws Exception {
    testShouldPersistOnDisk(context, StateStorageMode.PRUNE);
  }

  @TestTemplate
  public void shouldPersistOnDisk_archiveMode(final DatabaseContext context) throws Exception {
    testShouldPersistOnDisk(context, StateStorageMode.ARCHIVE);
  }

  @TestTemplate
  public void shouldRecreateAnchorStoreOnRestart(final DatabaseContext context) throws Exception {
    createStorageSystem(context, StateStorageMode.PRUNE, StoreConfig.createDefault(), false);
    // Set up database from an anchor point
    final UInt64 anchorEpoch = UInt64.valueOf(10);
    final SignedBlockAndState anchorBlockAndState =
        chainBuilder.generateBlockAtSlot(spec.computeStartSlotAtEpoch(anchorEpoch));
    final AnchorPoint anchor =
        AnchorPoint.create(
            spec, new Checkpoint(anchorEpoch, anchorBlockAndState.getRoot()), anchorBlockAndState);
    initFromAnchor(anchor);

    // Shutdown and restart
    restartStorage();

    final UpdatableStore memoryStore = recreateStore();
    assertStoresMatch(memoryStore, store);
    assertThat(memoryStore.getInitialCheckpoint()).contains(anchor.getCheckpoint());
    assertThat(database.getEarliestAvailableBlockSlot()).contains(anchorBlockAndState.getSlot());
  }

  @TestTemplate
  public void shouldThrowIfClosedDatabaseIsModified_setGenesis(final DatabaseContext context)
      throws Exception {
    initialize(context);
    database.close();
    assertThatThrownBy(() -> database.storeInitialAnchor(genesisAnchor))
        .isInstanceOf(ShuttingDownException.class);
  }

  @TestTemplate
  public void shouldThrowIfClosedDatabaseIsModified_update(final DatabaseContext context)
      throws Exception {
    initialize(context);
    database.storeInitialAnchor(genesisAnchor);
    database.close();

    final SignedBlockAndState newValue = chainBuilder.generateBlockAtSlot(1);
    // Sanity check
    assertThatSafeFuture(store.retrieveBlockState(newValue.getRoot()))
        .isCompletedWithEmptyOptional();
    final StoreTransaction transaction = recentChainData.startStoreTransaction();
    transaction.putBlockAndState(newValue, spec.calculateBlockCheckpoints(newValue.getState()));

    final SafeFuture<Void> result = transaction.commit();
    assertThatThrownBy(result::get).hasCauseInstanceOf(ShuttingDownException.class);
  }

  @TestTemplate
  public void createMemoryStore_priorToGenesisTime(final DatabaseContext context) throws Exception {
    initialize(context);
    database.storeInitialAnchor(genesisAnchor);

    final Optional<OnDiskStoreData> maybeData =
        ((KvStoreDatabase<?, ?, ?, ?>) database).createMemoryStore(() -> 0L);
    assertThat(maybeData).isNotEmpty();

    final OnDiskStoreData data = maybeData.get();
    final UpdatableStore store =
        StoreBuilder.create()
            .metricsSystem(new NoOpMetricsSystem())
            .specProvider(spec)
            .time(data.getTime())
            .anchor(data.getAnchor())
            .genesisTime(data.getGenesisTime())
            .latestFinalized(data.getLatestFinalized())
            .finalizedOptimisticTransitionPayload(data.getFinalizedOptimisticTransitionPayload())
            .justifiedCheckpoint(data.getJustifiedCheckpoint())
            .bestJustifiedCheckpoint(data.getBestJustifiedCheckpoint())
            .blockInformation(data.getBlockInformation())
            .votes(data.getVotes())
            .asyncRunner(mock(AsyncRunner.class))
            .blockProvider(mock(BlockProvider.class))
            .stateProvider(mock(StateAndBlockSummaryProvider.class))
            .build();

    assertThat(store.getTimeSeconds()).isEqualTo(genesisTime);
  }

  @TestTemplate
  public void shouldThrowIfClosedDatabaseIsRead_createMemoryStore(final DatabaseContext context)
      throws Exception {
    initialize(context);
    database.storeInitialAnchor(genesisAnchor);
    database.close();

    assertThatThrownBy(database::createMemoryStore).isInstanceOf(ShuttingDownException.class);
  }

  @TestTemplate
  public void shouldThrowIfClosedDatabaseIsRead_getSlotForFinalizedBlockRoot(
      final DatabaseContext context) throws Exception {
    initialize(context);
    database.storeInitialAnchor(genesisAnchor);
    database.close();

    assertThatThrownBy(() -> database.getSlotForFinalizedBlockRoot(Bytes32.ZERO))
        .isInstanceOf(ShuttingDownException.class);
  }

  @TestTemplate
  public void shouldThrowIfClosedDatabaseIsRead_getSignedBlock(final DatabaseContext context)
      throws Exception {
    initialize(context);
    database.storeInitialAnchor(genesisAnchor);
    database.close();

    assertThatThrownBy(() -> database.getSignedBlock(genesisCheckpoint.getRoot()))
        .isInstanceOf(ShuttingDownException.class);
  }

  @TestTemplate
  public void shouldThrowIfClosedDatabaseIsRead_streamFinalizedBlocks(final DatabaseContext context)
      throws Exception {
    initialize(context);
    database.storeInitialAnchor(genesisAnchor);
    database.close();

    assertThatThrownBy(() -> database.streamFinalizedBlocks(UInt64.ZERO, UInt64.ONE))
        .isInstanceOf(ShuttingDownException.class);
  }

  @TestTemplate
  public void shouldThrowIfClosedDatabaseIsRead_streamFinalizedBlocksShuttingDown(
      final DatabaseContext context) throws Exception {
    initialize(context);
    database.storeInitialAnchor(genesisAnchor);
    try (final Stream<SignedBeaconBlock> stream =
        database.streamFinalizedBlocks(UInt64.ZERO, UInt64.valueOf(1000L))) {
      database.close();
      assertThatThrownBy(stream::findAny).isInstanceOf(ShuttingDownException.class);
    }
  }

  @TestTemplate
  public void shouldThrowIfTransactionModifiedAfterDatabaseIsClosed_updateHotDao(
      final DatabaseContext context) throws Exception {
    initialize(context);
    database.storeInitialAnchor(genesisAnchor);

    try (final KvStoreCombinedDaoCommon.HotUpdaterCommon updater = hotUpdater()) {
      database.close();
      assertThatThrownBy(() -> updater.setGenesisTime(UInt64.ONE))
          .isInstanceOf(ShuttingDownException.class);
    }
  }

  @MustBeClosed
  private KvStoreCombinedDaoCommon.HotUpdaterCommon hotUpdater() {
    return ((KvStoreDatabase<?, ?, ?, ?>) database).hotUpdater();
  }

  @TestTemplate
  public void shouldThrowIfTransactionModifiedAfterDatabaseIsClosed_updateFinalizedDao(
      final DatabaseContext context) throws Exception {
    initialize(context);
    database.storeInitialAnchor(genesisAnchor);

    try (final KvStoreCombinedDaoCommon.FinalizedUpdaterCommon updater = finalizedUpdater()) {
      SignedBlockAndState newBlock = chainBuilder.generateNextBlock();
      database.close();
      assertThatThrownBy(() -> updater.addFinalizedState(newBlock.getRoot(), newBlock.getState()))
          .isInstanceOf(ShuttingDownException.class);
    }
  }

  @MustBeClosed
  private KvStoreCombinedDaoCommon.FinalizedUpdaterCommon finalizedUpdater() {
    return ((KvStoreDatabase<?, ?, ?, ?>) database).finalizedUpdater();
  }

  @TestTemplate
  public void shouldThrowIfTransactionModifiedAfterDatabaseIsClosed_updateEth1Dao(
      final DatabaseContext context) throws Exception {
    initialize(context);
    database.storeInitialAnchor(genesisAnchor);

    try (final KvStoreCombinedDaoCommon.HotUpdaterCommon updater = hotUpdater()) {
      final MinGenesisTimeBlockEvent genesisTimeBlockEvent =
          dataStructureUtil.randomMinGenesisTimeBlockEvent(1);
      database.close();
      assertThatThrownBy(() -> updater.addMinGenesisTimeBlock(genesisTimeBlockEvent))
          .isInstanceOf(ShuttingDownException.class);
    }
  }

  @TestTemplate
  public void shouldThrowIfClosedDatabaseIsRead_getHistoricalState(final DatabaseContext context)
      throws Exception {
    initialize(context);
    // Store genesis
    database.storeInitialAnchor(genesisAnchor);
    // Add a new finalized block to supersede genesis
    final SignedBlockAndState newBlock = chainBuilder.generateBlockAtSlot(1);
    final Checkpoint newCheckpoint = getCheckpointForBlock(newBlock.getBlock());
    final StoreTransaction transaction = recentChainData.startStoreTransaction();
    transaction.putBlockAndState(newBlock, spec.calculateBlockCheckpoints(newBlock.getState()));
    transaction.setFinalizedCheckpoint(newCheckpoint, false);
    transaction.commit().ifExceptionGetsHereRaiseABug();
    // Close db
    database.close();

    assertThatThrownBy(
            () ->
                database.getLatestAvailableFinalizedState(
                    genesisCheckpoint.getEpochStartSlot(spec)))
        .isInstanceOf(ShuttingDownException.class);
  }

  @TestTemplate
  public void shouldThrowIfTransactionModifiedAfterDatabaseIsClosedFromAnotherThread(
      final DatabaseContext context) throws Exception {

    for (int i = 0; i < 20; i++) {
      createStorageSystem(context, StateStorageMode.PRUNE, StoreConfig.createDefault(), false);
      database.storeInitialAnchor(genesisAnchor);

      try (final KvStoreCombinedDaoCommon.HotUpdaterCommon updater = hotUpdater()) {
        final Thread dbCloserThread =
            new Thread(
                () -> {
                  try {
                    database.close();
                  } catch (Exception e) {
                    throw new RuntimeException(e);
                  }
                });

        dbCloserThread.start();
        try {
          updater.setGenesisTime(UInt64.ONE);
        } catch (ShuttingDownException ignored) {
          // For this test to fail, we'd see exceptions other than ShuttingDownException.
          // Because it's a probabilistic test, it's possible that either no exception occurs, or
          // a ShuttingDownException, and both these outcomes are ok, but other exceptions are not.
        }

        dbCloserThread.join(500);
      }
    }
  }

  @TestTemplate
  public void shouldPruneHotBlocksOlderThanFinalizedSlotAfterRestart__archive(
      final DatabaseContext context) throws Exception {
    testShouldPruneHotBlocksOlderThanFinalizedSlotAfterRestart(context, StateStorageMode.ARCHIVE);
  }

  @TestTemplate
  public void shouldPruneHotBlocksOlderThanFinalizedSlotAfterRestart__prune(
      final DatabaseContext context) throws Exception {
    testShouldPruneHotBlocksOlderThanFinalizedSlotAfterRestart(context, StateStorageMode.PRUNE);
  }

  @TestTemplate
  public void shouldPersistHotStates_everyEpoch(final DatabaseContext context) throws Exception {
    final int storageFrequency = 1;
    final UInt64 latestEpoch = UInt64.valueOf(3);
    final UInt64 targetSlot = spec.computeStartSlotAtEpoch(latestEpoch);
    addBlocksWithHotStatePersistence(context, storageFrequency, targetSlot.intValue());

    // We should only be able to pull states at epoch boundaries
    final Set<UInt64> epochBoundarySlots = getEpochBoundarySlots(3);
    for (int i = 0; i <= targetSlot.intValue(); i++) {
      final SignedBlockAndState blockAndState = chainBuilder.getBlockAndStateAtSlot(i);
      final Optional<BeaconState> actual = database.getHotState(blockAndState.getRoot());

      if (epochBoundarySlots.contains(UInt64.valueOf(i))) {
        assertThat(actual).contains(blockAndState.getState());
      } else {
        assertThat(actual).isEmpty();
      }
    }
  }

  @TestTemplate
  public void shouldPersistHotStates_never(final DatabaseContext context) throws Exception {
    final int storageFrequency = 0;
    final UInt64 latestEpoch = UInt64.valueOf(3);
    final UInt64 targetSlot = spec.computeStartSlotAtEpoch(latestEpoch);
    addBlocksWithHotStatePersistence(context, storageFrequency, targetSlot.intValue());

    for (int i = 0; i <= targetSlot.intValue(); i++) {
      final SignedBlockAndState blockAndState = chainBuilder.getBlockAndStateAtSlot(i);
      final Optional<BeaconState> actual = database.getHotState(blockAndState.getRoot());
      assertThat(actual).isEmpty();
    }
  }

  private void addBlocksWithHotStatePersistence(
      final DatabaseContext context, final int statePersistenceInEpochs, final int targetSlot)
      throws IOException {
    final StoreConfig storeConfig =
        StoreConfig.builder()
            .hotStatePersistenceFrequencyInEpochs(statePersistenceInEpochs)
            .build();
    createStorageSystem(context, StateStorageMode.ARCHIVE, storeConfig, false);
    initGenesis();
    chainBuilder.generateBlocksUpToSlot(targetSlot);

    // Add blocks
    addBlocks(chainBuilder.streamBlocksAndStates().collect(toList()));
  }

  @TestTemplate
  public void shouldPersistHotStates_everyThirdEpoch(final DatabaseContext context)
      throws IOException {
    final int storageFrequency = 3;
    final UInt64 latestEpoch = UInt64.valueOf(3 * storageFrequency);
    final UInt64 targetSlot = spec.computeStartSlotAtEpoch(latestEpoch);
    addBlocksWithHotStatePersistence(context, storageFrequency, targetSlot.intValue());

    // We should only be able to pull states at epoch boundaries
    final Set<UInt64> epochBoundarySlots = getEpochBoundarySlots(latestEpoch.intValue());
    for (int i = 0; i <= targetSlot.intValue(); i++) {
      final SignedBlockAndState blockAndState = chainBuilder.getBlockAndStateAtSlot(i);
      final Optional<BeaconState> actual = database.getHotState(blockAndState.getRoot());

      final UInt64 currentSlot = UInt64.valueOf(i);
      final UInt64 currentEpoch = spec.computeEpochAtSlot(currentSlot);
      final boolean shouldPersistThisEpoch = currentEpoch.mod(storageFrequency).equals(UInt64.ZERO);
      if (epochBoundarySlots.contains(currentSlot) && shouldPersistThisEpoch) {
        assertThat(actual).contains(blockAndState.getState());
      } else {
        assertThat(actual).isEmpty();
      }
    }
  }

  @TestTemplate
  public void shouldClearStaleHotStates(final DatabaseContext context) throws IOException {
    final int storageFrequency = 1;
    final UInt64 latestEpoch = UInt64.valueOf(3);
    final UInt64 targetSlot = spec.computeStartSlotAtEpoch(latestEpoch);
    addBlocksWithHotStatePersistence(context, storageFrequency, targetSlot.intValue());
    justifyAndFinalizeEpoch(latestEpoch, chainBuilder.getLatestBlockAndState());

    // Hot states should be cleared out
    for (int i = 0; i <= targetSlot.intValue(); i++) {
      final SignedBlockAndState blockAndState = chainBuilder.getBlockAndStateAtSlot(i);
      final Optional<BeaconState> actual = database.getHotState(blockAndState.getRoot());
      assertThat(actual).isEmpty();
    }
  }

  @TestTemplate
  public void shouldHandleRestartWithUnrecoverableForkBlocks_archive(final DatabaseContext context)
      throws IOException {
    testShouldHandleRestartWithUnrecoverableForkBlocks(context, StateStorageMode.ARCHIVE);
  }

  @TestTemplate
  public void shouldHandleRestartWithUnrecoverableForkBlocks_prune(final DatabaseContext context)
      throws IOException {
    testShouldHandleRestartWithUnrecoverableForkBlocks(context, StateStorageMode.PRUNE);
  }

  private void testShouldPruneHotBlocksOlderThanFinalizedSlotAfterRestart(
      final DatabaseContext context, final StateStorageMode storageMode) throws IOException {
    final long finalizedSlot = 7;
    final int hotBlockCount = 3;
    // Setup chains
    chainBuilder.generateBlocksUpToSlot(finalizedSlot);
    SignedBlockAndState finalizedBlock = chainBuilder.getBlockAndStateAtSlot(finalizedSlot);
    final Checkpoint finalizedCheckpoint = getCheckpointForBlock(finalizedBlock.getBlock());
    final long firstHotBlockSlot =
        finalizedCheckpoint.getEpochStartSlot(spec).plus(UInt64.ONE).longValue();
    for (int i = 0; i < hotBlockCount; i++) {
      chainBuilder.generateBlockAtSlot(firstHotBlockSlot + i);
    }
    final long lastSlot = chainBuilder.getLatestSlot().longValue();

    // Setup database
    createStorageSystem(context, storageMode, StoreConfig.createDefault(), false);
    initGenesis();

    add(chainBuilder.streamBlocksAndStates().collect(Collectors.toSet()));

    // Close database and rebuild from disk
    restartStorage();

    // Ensure all states are actually regenerated in memory since we expect every state to be stored
    chainBuilder
        .streamBlocksAndStates()
        .forEach(
            blockAndState -> recentChainData.retrieveBlockState(blockAndState.getRoot()).join());

    justifyAndFinalizeEpoch(finalizedCheckpoint.getEpoch(), finalizedBlock);

    // We should be able to access hot blocks and state
    final List<SignedBlockAndState> expectedHotBlocksAndStates =
        chainBuilder.streamBlocksAndStates(finalizedSlot, lastSlot).collect(toList());
    assertHotBlocksAndStatesInclude(expectedHotBlocksAndStates);

    final Map<Bytes32, BeaconState> historicalStates =
        chainBuilder
            .streamBlocksAndStates(0, 6)
            .collect(Collectors.toMap(SignedBlockAndState::getRoot, SignedBlockAndState::getState));

    switch (storageMode) {
      case ARCHIVE:
        assertFinalizedStatesAvailable(historicalStates);
        break;
      case PRUNE:
        assertStatesUnavailable(
            historicalStates.values().stream().map(BeaconState::getSlot).collect(toList()));
        break;
    }
  }

  private void testShouldHandleRestartWithUnrecoverableForkBlocks(
      final DatabaseContext context, final StateStorageMode storageMode) throws IOException {
    createStorageSystem(context, storageMode, StoreConfig.createDefault(), false);
    final CreateForkChainResult forkChainResult = createForkChain(true);

    // Fork states should be unavailable
    final UInt64 firstHotBlockSlot = forkChainResult.getFirstHotBlockSlot();
    final List<Bytes32> unavailableBlockRoots =
        forkChainResult
            .getForkChain()
            .streamBlocksAndStates(4, firstHotBlockSlot.longValue())
            .map(SignedBlockAndState::getRoot)
            .collect(Collectors.toList());
    final List<UInt64> unavailableBlockSlots =
        forkChainResult
            .getForkChain()
            .streamBlocksAndStates(4, firstHotBlockSlot.longValue())
            .map(SignedBlockAndState::getSlot)
            .collect(Collectors.toList());
    assertStatesUnavailable(unavailableBlockSlots);
    assertBlocksUnavailable(unavailableBlockRoots);
  }

  private Set<UInt64> getEpochBoundarySlots(final int toEpoch) {
    final Set<UInt64> epochBoundarySlots = new HashSet<>();
    for (int i = 1; i <= toEpoch; i++) {
      final UInt64 epochSlot = spec.computeStartSlotAtEpoch(UInt64.valueOf(i));
      epochBoundarySlots.add(epochSlot);
    }
    return epochBoundarySlots;
  }

  private void testShouldPersistOnDisk(
      final DatabaseContext context, final StateStorageMode storageMode) throws Exception {
    testShouldRecordFinalizedBlocksAndStates(context, storageMode, false);
  }

  private CreateForkChainResult createForkChain(final boolean restartStorage) {
    // Setup chains
    // Both chains share block up to slot 3
    final ChainBuilder primaryChain = ChainBuilder.create(spec, VALIDATOR_KEYS);
    primaryChain.generateGenesis(genesisTime, true);
    primaryChain.generateBlocksUpToSlot(3);
    final ChainBuilder forkChain = primaryChain.fork();
    // Primary chain's next block is at 7
    final SignedBlockAndState finalizedBlock = primaryChain.generateBlockAtSlot(7);
    final Checkpoint finalizedCheckpoint = getCheckpointForBlock(primaryChain.getBlockAtSlot(7));
    final UInt64 firstHotBlockSlot = finalizedCheckpoint.getEpochStartSlot(spec).plus(UInt64.ONE);
    primaryChain.generateBlockAtSlot(firstHotBlockSlot);
    // Fork chain's next block is at 6
    forkChain.generateBlockAtSlot(6);
    forkChain.generateBlockAtSlot(firstHotBlockSlot);

    // Setup database

    initGenesis();

    final Set<SignedBlockAndState> allBlocksAndStates =
        Streams.concat(primaryChain.streamBlocksAndStates(), forkChain.streamBlocksAndStates())
            .collect(Collectors.toSet());

    // Finalize at block 7, making the fork blocks unavailable
    add(allBlocksAndStates);
    justifyAndFinalizeEpoch(finalizedCheckpoint.getEpoch(), finalizedBlock);

    if (restartStorage) {
      // Close database and rebuild from disk
      restartStorage();
    }
    return new CreateForkChainResult(forkChain, firstHotBlockSlot);
  }

  public void testStartupFromNonGenesisState(
      final DatabaseContext context, final StateStorageMode storageMode) throws IOException {
    createStorageSystem(context, storageMode, StoreConfig.createDefault(), false);

    // Set up database from an anchor point
    final UInt64 anchorEpoch = UInt64.valueOf(10);
    final SignedBlockAndState anchorBlockAndState =
        chainBuilder.generateBlockAtSlot(spec.computeStartSlotAtEpoch(anchorEpoch));
    final AnchorPoint anchor =
        AnchorPoint.create(
            spec,
            new Checkpoint(anchorEpoch, anchorBlockAndState.getRoot()),
            anchorBlockAndState.getState(),
            Optional.empty());
    initFromAnchor(anchor);

    // Add some blocks
    addBlocks(chainBuilder.generateNextBlock(), chainBuilder.generateNextBlock());

    // Restart and check data is what we expect
    final UpdatableStore originalStore = recentChainData.getStore();
    restartStorage();

    StoreAssertions.assertStoresMatch(recentChainData.getStore(), originalStore);
    assertThat(recentChainData.getFinalizedCheckpoint()).contains(anchor.getCheckpoint());
  }

  @TestTemplate
  public void startupFromNonGenesisStateAndFinalizeNewCheckpoint_prune(
      final DatabaseContext context) throws IOException {
    testStartupFromNonGenesisStateAndFinalizeNewCheckpoint(context, StateStorageMode.PRUNE);
  }

  @TestTemplate
  public void startupFromNonGenesisStateAndFinalizeNewCheckpoint_archive(
      final DatabaseContext context) throws IOException {
    testStartupFromNonGenesisStateAndFinalizeNewCheckpoint(context, StateStorageMode.ARCHIVE);
  }

  @TestTemplate
  public void startupFromNonGenesisAndFinalizeShouldStoreFinalizedBlocksCorrectly(
      final DatabaseContext context) throws IOException {
    final ChainBuilder primaryChain = ChainBuilder.create(spec, VALIDATOR_KEYS);
    primaryChain.generateGenesis(genesisTime, true);
    primaryChain.generateBlocksUpToSlot(6);
    final SignedBlockAndState finalizedBlock = primaryChain.generateBlockAtSlot(7);
    final Checkpoint finalizedCheckpoint = getCheckpointForBlock(finalizedBlock.getBlock());
    final List<SignedBlockAndState> hotData = primaryChain.generateBlocksUpToSlot(21);
    // hot data includes the finalized block also
    hotData.add(finalizedBlock);

    // Setup database
    initialize(context, StateStorageMode.PRUNE);
    final Set<SignedBlockAndState> allBlocksAndStates =
        primaryChain.streamBlocksAndStates().collect(Collectors.toSet());
    addBlocks(new ArrayList<>(allBlocksAndStates));
    justifyAndFinalizeEpoch(finalizedCheckpoint.getEpoch(), finalizedBlock);
    final Set<Bytes32> blocksToPrune =
        primaryChain
            .streamBlocksAndStates(0, finalizedBlock.getSlot().longValue() - 1)
            .map(SignedBlockAndState::getRoot)
            .collect(Collectors.toSet());
    assertRecentDataWasPruned(store, blocksToPrune);
    restartStorage();

    // After restarting storage, we finalize a new block. This results in a number of finalized
    // blocks being pushed in the update that aren't currently hot blocks,
    // and they should end up in finalized storage within the same transaction
    final SignedBlockAndState finalizedBlock2 = hotData.get(5);
    final Checkpoint finalizedCheckpoint2 = getCheckpointForBlock(finalizedBlock2.getBlock());
    justifyAndFinalizeEpoch(finalizedCheckpoint2.getEpoch(), finalizedBlock2);

    // Check finalized data
    final List<SignedBeaconBlock> expectedFinalizedBlocks =
        primaryChain
            .streamBlocksAndStates(0, 12)
            .map(SignedBlockAndState::getBlock)
            .collect(toList());
    assertBlocksFinalized(expectedFinalizedBlocks);
  }

  @TestTemplate
  void shouldStoreAndRetrieveVotes(final DatabaseContext context) throws IOException {
    createStorageSystem(context, StateStorageMode.PRUNE, StoreConfig.createDefault(), false);
    assertThat(database.getVotes()).isEmpty();

    final Map<UInt64, VoteTracker> voteBatch1 =
        Map.of(
            UInt64.valueOf(10), dataStructureUtil.randomVoteTracker(),
            UInt64.valueOf(11), dataStructureUtil.randomVoteTracker(),
            UInt64.valueOf(12), dataStructureUtil.randomVoteTracker());
    database.storeVotes(voteBatch1);

    assertThat(database.getVotes()).isEqualTo(voteBatch1);

    final Map<UInt64, VoteTracker> voteBatch2 =
        Map.of(
            UInt64.valueOf(10), dataStructureUtil.randomVoteTracker(),
            UInt64.valueOf(13), dataStructureUtil.randomVoteTracker());
    database.storeVotes(voteBatch2);

    final Map<UInt64, VoteTracker> expected = new HashMap<>(voteBatch1);
    expected.putAll(voteBatch2);
    assertThat(database.getVotes()).isEqualTo(expected);
  }

  @TestTemplate
  public void shouldStoreFinalizedState(final DatabaseContext context) throws IOException {
    createStorageSystem(context, StateStorageMode.ARCHIVE, StoreConfig.createDefault(), false);
    assertThat(database.getLatestAvailableFinalizedState(ONE)).isEmpty();

    final BeaconState beaconState = dataStructureUtil.randomBeaconState(ONE);
    database.storeFinalizedState(beaconState, dataStructureUtil.randomBytes32());

    assertThat(database.getLatestAvailableFinalizedState(ONE)).isEqualTo(Optional.of(beaconState));
  }

  public void testStartupFromNonGenesisStateAndFinalizeNewCheckpoint(
      final DatabaseContext context, final StateStorageMode storageMode) throws IOException {
    createStorageSystem(context, storageMode, StoreConfig.createDefault(), false);

    // Set up database from an anchor point
    final UInt64 anchorEpoch = UInt64.valueOf(10);
    final SignedBlockAndState anchorBlockAndState =
        chainBuilder.generateBlockAtSlot(spec.computeStartSlotAtEpoch(anchorEpoch));
    final AnchorPoint anchor =
        AnchorPoint.create(
            spec,
            new Checkpoint(anchorEpoch, anchorBlockAndState.getRoot()),
            anchorBlockAndState.getState(),
            Optional.empty());
    initFromAnchor(anchor);

    // Add some blocks
    addBlocks(chainBuilder.generateNextBlock(), chainBuilder.generateNextBlock());
    // And finalize them
    final SignedBlockAndState newFinalizedBlockAndState = chainBuilder.getLatestBlockAndState();
    final UInt64 newFinalizedEpoch = anchorEpoch.plus(1);
    justifyAndFinalizeEpoch(newFinalizedEpoch, newFinalizedBlockAndState);

    // Restart and check data is what we expect
    final UpdatableStore originalStore = recentChainData.getStore();
    restartStorage();

    StoreAssertions.assertStoresMatch(recentChainData.getStore(), originalStore);
    assertThat(recentChainData.getFinalizedCheckpoint())
        .contains(new Checkpoint(newFinalizedEpoch, newFinalizedBlockAndState.getRoot()));
  }

  private Bytes32 insertRandomSlotAndBlock(
      final long slot, final DataStructureUtil dataStructureUtil) {
    final Bytes32 stateRoot = dataStructureUtil.randomBytes32();
    final SlotAndBlockRoot slotAndBlockRoot =
        new SlotAndBlockRoot(UInt64.valueOf(slot), dataStructureUtil.randomBytes32());
    database.addHotStateRoots(Map.of(stateRoot, slotAndBlockRoot));
    return stateRoot;
  }

  private void testShouldRecordFinalizedBlocksAndStates(
      final DatabaseContext context, final StateStorageMode storageMode, final boolean batchUpdate)
      throws IOException {
    // Setup chains
    // Both chains share block up to slot 3
    final ChainBuilder primaryChain = ChainBuilder.create(spec, VALIDATOR_KEYS);
    primaryChain.generateGenesis(genesisTime, true);
    primaryChain.generateBlocksUpToSlot(3);
    final ChainBuilder forkChain = primaryChain.fork();
    // Fork chain's next block is at 6
    forkChain.generateBlockAtSlot(6);
    forkChain.generateBlocksUpToSlot(7);
    // Primary chain's next block is at 7
    final SignedBlockAndState finalizedBlock = primaryChain.generateBlockAtSlot(7);
    final Checkpoint finalizedCheckpoint = getCheckpointForBlock(finalizedBlock.getBlock());
    final UInt64 pruneToSlot = finalizedCheckpoint.getEpochStartSlot(spec);
    // Add some blocks in the next epoch
    final UInt64 hotSlot = pruneToSlot.plus(UInt64.ONE);
    primaryChain.generateBlockAtSlot(hotSlot);
    forkChain.generateBlockAtSlot(hotSlot);

    // Setup database
    initialize(context, storageMode);

    final Set<SignedBlockAndState> allBlocksAndStates =
        Streams.concat(primaryChain.streamBlocksAndStates(), forkChain.streamBlocksAndStates())
            .collect(Collectors.toSet());

    if (batchUpdate) {
      final StoreTransaction transaction = recentChainData.startStoreTransaction();
      add(transaction, allBlocksAndStates);
      justifyAndFinalizeEpoch(finalizedCheckpoint.getEpoch(), finalizedBlock, transaction);
      assertThat(transaction.commit()).isCompleted();
    } else {
      add(allBlocksAndStates);
      justifyAndFinalizeEpoch(finalizedCheckpoint.getEpoch(), finalizedBlock);
    }

    // Upon finalization, we should prune data
    final Set<Bytes32> blocksToPrune =
        Streams.concat(
                primaryChain.streamBlocksAndStates(0, pruneToSlot.longValue()),
                forkChain.streamBlocksAndStates(0, pruneToSlot.longValue()))
            .map(SignedBlockAndState::getRoot)
            .collect(Collectors.toSet());
    blocksToPrune.remove(finalizedBlock.getRoot());

    // Check data was pruned from store
    assertRecentDataWasPruned(store, blocksToPrune);

    restartStorage();

    // Check hot data
    final List<SignedBlockAndState> expectedHotBlocksAndStates =
        List.of(finalizedBlock, primaryChain.getBlockAndStateAtSlot(hotSlot));
    assertHotBlocksAndStates(store, expectedHotBlocksAndStates);
    final SignedBlockAndState prunedForkBlock = forkChain.getBlockAndStateAtSlot(hotSlot);
    assertThat(store.containsBlock(prunedForkBlock.getRoot())).isFalse();

    // Check finalized data
    final List<SignedBeaconBlock> expectedFinalizedBlocks =
        primaryChain
            .streamBlocksAndStates(0, 7)
            .map(SignedBlockAndState::getBlock)
            .collect(toList());
    assertBlocksFinalized(expectedFinalizedBlocks);
    assertGetLatestFinalizedRootAtSlotReturnsFinalizedBlocks(expectedFinalizedBlocks);
    assertBlocksAvailableByRoot(expectedFinalizedBlocks);
    assertFinalizedBlocksAvailableViaStream(
        1,
        3,
        primaryChain.getBlockAtSlot(1),
        primaryChain.getBlockAtSlot(2),
        primaryChain.getBlockAtSlot(3));

    switch (storageMode) {
      case ARCHIVE:
        // Finalized states should be available
        final Map<Bytes32, BeaconState> expectedStates =
            primaryChain
                .streamBlocksAndStates(0, 7)
                .collect(toMap(SignedBlockAndState::getRoot, SignedBlockAndState::getState));
        assertFinalizedStatesAvailable(expectedStates);
        break;
      case PRUNE:
        // Check pruned states
        final List<UInt64> unavailableSlots =
            allBlocksAndStates.stream().map(SignedBlockAndState::getSlot).collect(toList());
        assertStatesUnavailable(unavailableSlots);
        break;
    }
  }

  private void assertFinalizedBlocksAvailableViaStream(
      final int fromSlot, final int toSlot, final SignedBeaconBlock... expectedBlocks) {
    try (final Stream<SignedBeaconBlock> stream =
        database.streamFinalizedBlocks(UInt64.valueOf(fromSlot), UInt64.valueOf(toSlot))) {
      assertThat(stream).containsExactly(expectedBlocks);
    }
  }

  private void assertBlocksFinalized(final List<SignedBeaconBlock> blocks) {
    for (SignedBeaconBlock block : blocks) {
      assertThat(database.getFinalizedBlockAtSlot(block.getSlot()))
          .describedAs("Block at slot %s", block.getSlot())
          .contains(block);
    }
  }

  private void assertBlocksAvailableByRoot(final List<SignedBeaconBlock> blocks) {
    for (SignedBeaconBlock block : blocks) {
      assertThat(database.getSignedBlock(block.getRoot()))
          .describedAs("Block root at slot %s", block.getSlot())
          .contains(block);
    }
  }

  private void assertGetLatestFinalizedRootAtSlotReturnsFinalizedBlocks(
      final List<SignedBeaconBlock> blocks) {
    final SignedBeaconBlock genesisBlock =
        database.getFinalizedBlockAtSlot(GENESIS_SLOT).orElseThrow();

    final List<SignedBeaconBlock> finalizedBlocks = new ArrayList<>();
    finalizedBlocks.add(genesisBlock);
    finalizedBlocks.addAll(blocks);
    for (int i = 1; i < finalizedBlocks.size(); i++) {
      final SignedBeaconBlock currentBlock = finalizedBlocks.get(i - 1);
      final SignedBeaconBlock nextBlock = finalizedBlocks.get(i);
      // All slots from the current block up to and excluding the next block should return the
      // current block
      for (long slot = currentBlock.getSlot().longValue();
          slot < nextBlock.getSlot().longValue();
          slot++) {
        assertThat(database.getLatestFinalizedBlockAtSlot(UInt64.valueOf(slot)))
            .describedAs("Latest finalized block at slot %s", slot)
            .contains(currentBlock);
      }
    }

    // Check that last block
    final SignedBeaconBlock lastFinalizedBlock = finalizedBlocks.get(finalizedBlocks.size() - 1);
    for (int i = 0; i < 10; i++) {
      final UInt64 slot = lastFinalizedBlock.getSlot().plus(i);
      assertThat(database.getLatestFinalizedBlockAtSlot(slot))
          .describedAs("Latest finalized block at slot %s", slot)
          .contains(lastFinalizedBlock);
    }
  }

  private void assertHotBlocksAndStates(
      final UpdatableStore store, final Collection<SignedBlockAndState> blocksAndStates) {
    final List<UpdatableStore> storesToCheck = List.of(store, recreateStore());
    for (UpdatableStore currentStore : storesToCheck) {
      assertThat(currentStore.getOrderedBlockRoots())
          .hasSameElementsAs(
              blocksAndStates.stream().map(SignedBlockAndState::getRoot).collect(toList()));

      final List<BeaconState> hotStates =
          currentStore.getOrderedBlockRoots().stream()
              .map(currentStore::retrieveBlockState)
              .map(
                  f -> {
                    assertThat(f).isCompleted();
                    return f.join();
                  })
              .flatMap(Optional::stream)
              .collect(toList());

      assertThat(hotStates)
          .hasSameElementsAs(
              blocksAndStates.stream().map(SignedBlockAndState::getState).collect(toList()));
    }
  }

  private void assertHotBlocksAndStatesInclude(
      final Collection<SignedBlockAndState> blocksAndStates) {
    final UpdatableStore memoryStore = recreateStore();
    assertThat(memoryStore.getOrderedBlockRoots())
        .containsAll(blocksAndStates.stream().map(SignedBlockAndState::getRoot).collect(toList()));

    final List<BeaconState> hotStates =
        memoryStore.getOrderedBlockRoots().stream()
            .map(memoryStore::retrieveBlockState)
            .map(
                f -> {
                  assertThat(f).isCompleted();
                  return f.join();
                })
            .flatMap(Optional::stream)
            .collect(toList());

    assertThat(hotStates)
        .containsAll(blocksAndStates.stream().map(SignedBlockAndState::getState).collect(toList()));
  }

  private void assertFinalizedStatesAvailable(final Map<Bytes32, BeaconState> states) {
    for (BeaconState state : states.values()) {
      assertThat(database.getLatestAvailableFinalizedState(state.getSlot())).contains(state);
    }
  }

  private void assertStatesUnavailable(final Collection<UInt64> slots) {
    for (UInt64 slot : slots) {
      Optional<BeaconState> bs =
          database
              .getLatestAvailableFinalizedState(slot)
              .filter(state -> state.getSlot().equals(slot));
      assertThat(bs).isEmpty();
    }
  }

  private void assertBlocksUnavailable(final Collection<Bytes32> roots) {
    for (Bytes32 root : roots) {
      Optional<SignedBeaconBlock> bb = database.getSignedBlock(root);
      assertThat(bb).isEmpty();
    }
  }

  private void assertBlocksAvailable(final Collection<SignedBeaconBlock> blocks) {
    for (SignedBeaconBlock expectedBlock : blocks) {
      Optional<SignedBeaconBlock> actualBlock = database.getSignedBlock(expectedBlock.getRoot());
      assertThat(actualBlock).contains(expectedBlock);
    }
  }

  private void assertRecentDataWasPruned(
      final UpdatableStore store, final Set<Bytes32> prunedBlocks) {
    int index = 0;
    for (Bytes32 prunedBlock : prunedBlocks) {
      // Check pruned data has been removed from store
      assertThat(store.containsBlock(prunedBlock))
          .withFailMessage(
              "Store should not contain pruned block: " + prunedBlock + " (" + index + ")")
          .isFalse();
      assertThatSafeFuture(store.retrieveBlock(prunedBlock))
          .withFailMessage(
              "Store should not return a block from: " + prunedBlock + " (" + index + ")")
          .isCompletedWithEmptyOptional();
      assertThatSafeFuture(store.retrieveBlockState(prunedBlock))
          .withFailMessage(
              "Store should not return a state from: " + prunedBlock + " (" + index + ")")
          .isCompletedWithEmptyOptional();

      // Check hot data was pruned from db
      assertThat(database.getHotBlocks(Set.of(prunedBlock))).isEmpty();
      assertThat(database.getHotState(prunedBlock)).isEmpty();
      index++;
    }
  }

  private void addBlocks(final SignedBlockAndState... blocks) {
    addBlocks(Arrays.asList(blocks));
  }

  private void addBlocks(final List<SignedBlockAndState> blocks) {
    final StoreTransaction transaction = recentChainData.startStoreTransaction();
    for (SignedBlockAndState block : blocks) {
      transaction.putBlockAndState(block, spec.calculateBlockCheckpoints(block.getState()));
    }
    commit(transaction);
  }

  private void add(final Collection<SignedBlockAndState> blocks) {
    final StoreTransaction transaction = recentChainData.startStoreTransaction();
    add(transaction, blocks);
    commit(transaction);
  }

  private void add(
      final StoreTransaction transaction, final Collection<SignedBlockAndState> blocksAndStates) {
    blocksAndStates.stream()
        .sorted(Comparator.comparing(SignedBlockAndState::getSlot))
        .forEach(
            blockAndState ->
                transaction.putBlockAndState(
                    blockAndState, spec.calculateBlockCheckpoints(blockAndState.getState())));
  }

  private void justifyAndFinalizeEpoch(final UInt64 epoch, final SignedBlockAndState block) {
    StoreTransaction tx = recentChainData.startStoreTransaction();
    justifyAndFinalizeEpoch(epoch, block, tx);
    assertThat(tx.commit()).isCompleted();
  }

  private void justifyAndFinalizeEpoch(
      final UInt64 epoch, final SignedBlockAndState block, final StoreTransaction tx) {
    justifyEpoch(epoch, block, tx);
    finalizeEpoch(epoch, block, tx);
  }

  private void finalizeEpoch(
      final UInt64 epoch, final SignedBlockAndState block, final StoreTransaction transaction) {
    final Checkpoint checkpoint = new Checkpoint(epoch, block.getRoot());
    transaction.setFinalizedCheckpoint(checkpoint, false);
  }

  private void justifyEpoch(
      final UInt64 epoch, final SignedBlockAndState block, final StoreTransaction transaction) {
    final Checkpoint checkpoint = new Checkpoint(epoch, block.getRoot());
    transaction.setJustifiedCheckpoint(checkpoint);
  }

  private Checkpoint getCheckpointForBlock(final SignedBeaconBlock block) {
    final UInt64 blockEpoch = spec.computeEpochAtSlot(block.getSlot());
    final UInt64 blockEpochBoundary = spec.computeStartSlotAtEpoch(blockEpoch);
    final UInt64 checkpointEpoch =
        equivalentLongs(block.getSlot(), blockEpochBoundary) ? blockEpoch : blockEpoch.plus(ONE);
    return new Checkpoint(checkpointEpoch, block.getMessage().hashTreeRoot());
  }

  private boolean equivalentLongs(final UInt64 valA, final UInt64 valB) {
    return valA.compareTo(valB) == 0;
  }

  private void initGenesis() {
    recentChainData.initializeFromGenesis(genesisBlockAndState.getState(), UInt64.ZERO);
    store = recentChainData.getStore();
  }

  private void initFromAnchor(final AnchorPoint anchor) {
    recentChainData.initializeFromAnchorPoint(anchor, UInt64.ZERO);
    store = recentChainData.getStore();
  }

  private void generateCheckpoints() {
    while (chainBuilder.getLatestEpoch().longValue() < 3) {
      chainBuilder.generateNextBlock();
    }

    checkpoint1BlockAndState = chainBuilder.getLatestBlockAndStateAtEpochBoundary(1);
    checkpoint1 = chainBuilder.getCurrentCheckpointForEpoch(1);
    checkpoint2BlockAndState = chainBuilder.getLatestBlockAndStateAtEpochBoundary(2);
    checkpoint2 = chainBuilder.getCurrentCheckpointForEpoch(2);
    checkpoint3BlockAndState = chainBuilder.getLatestBlockAndStateAtEpochBoundary(3);
    checkpoint3 = chainBuilder.getCurrentCheckpointForEpoch(3);
  }

  private UpdatableStore recreateStore() {
    restartStorage();
    return storageSystem.recentChainData().getStore();
  }

  private void createStorageSystem(
      final DatabaseContext context,
      final StateStorageMode storageMode,
      final StoreConfig storeConfig,
      final boolean storeNonCanonicalBlocks)
      throws IOException {
    this.storageMode = storageMode;
    if (context.isInMemoryStorage()) {
      setDefaultStorage(
          context.createInMemoryStorage(spec, storageMode, storeConfig, storeNonCanonicalBlocks));
    } else {
      final Path tmpDir = Files.createTempDirectory("storageTest");
      tmpDirectories.add(tmpDir.toFile());
      setDefaultStorage(
          context.createFileBasedStorage(
              spec, tmpDir, storageMode, storeConfig, storeNonCanonicalBlocks));
    }
  }

  private void setDefaultStorage(final StorageSystem storageSystem) {
    this.storageSystem = storageSystem;
    database = storageSystem.database();
    recentChainData = storageSystem.recentChainData();
    storageSystems.add(storageSystem);
  }

  public static class CreateForkChainResult {
    private final ChainBuilder forkChain;
    private final UInt64 firstHotBlockSlot;

    public CreateForkChainResult(final ChainBuilder forkChain, final UInt64 firstHotBlockSlot) {
      this.forkChain = forkChain;
      this.firstHotBlockSlot = firstHotBlockSlot;
    }

    public ChainBuilder getForkChain() {
      return forkChain;
    }

    public UInt64 getFirstHotBlockSlot() {
      return firstHotBlockSlot;
    }
  }
}
