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

package tech.pegasys.teku.validator.coordinator;

import static java.util.Collections.emptyList;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;

import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.ethereum.pow.api.DepositTreeSnapshot;
import tech.pegasys.teku.ethereum.pow.api.DepositsFromBlockEvent;
import tech.pegasys.teku.ethereum.pow.api.Eth1EventsChannel;
import tech.pegasys.teku.ethereum.pow.api.MinGenesisTimeBlockEvent;
import tech.pegasys.teku.ethereum.pow.merkletree.DepositTree;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBytes32Vector;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBytes32VectorSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.DepositWithIndex;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.util.DepositUtil;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;
import tech.pegasys.teku.storage.client.RecentChainData;

public class DepositProvider
    implements SlotEventsChannel, Eth1EventsChannel, FinalizedCheckpointChannel {

  private static final Logger LOG = LogManager.getLogger();

  private final EventLogger eventLogger;

  private final RecentChainData recentChainData;
  private final Eth1DataCache eth1DataCache;
  private DepositTree depositMerkleTree;

  private final NavigableMap<UInt64, DepositWithIndex> depositNavigableMap = new TreeMap<>();
  private final Counter depositCounter;
  private final Spec spec;
  private final DepositsSchemaCache depositsSchemaCache = new DepositsSchemaCache();
  private final DepositUtil depositUtil;
  private final boolean useMissingDepositEventLogging;
  private boolean inSync = false;

  public DepositProvider(
      MetricsSystem metricsSystem,
      RecentChainData recentChainData,
      final Eth1DataCache eth1DataCache,
      final Spec spec,
      final EventLogger eventLogger,
      final boolean useMissingDepositEventLogging) {
    this.eventLogger = eventLogger;
    this.recentChainData = recentChainData;
    this.eth1DataCache = eth1DataCache;
    this.spec = spec;
    depositUtil = new DepositUtil(spec);
    depositMerkleTree = new DepositTree();
    depositCounter =
        metricsSystem.createCounter(
            TekuMetricCategory.BEACON,
            "eth1_deposit_total",
            "Total number of received ETH1 deposits");
    this.useMissingDepositEventLogging = useMissingDepositEventLogging;
  }

  @Override
  public synchronized void onDepositsFromBlock(DepositsFromBlockEvent event) {
    event.getDeposits().stream()
        .map(depositUtil::convertDepositEventToOperationDeposit)
        .forEach(
            deposit -> {
              if (!recentChainData.isPreGenesis()) {
                LOG.debug("About to process deposit: {}", deposit.getIndex());
              }

              depositNavigableMap.put(deposit.getIndex(), deposit);
              depositMerkleTree.pushLeaf(deposit.getData().hashTreeRoot());
            });
    depositCounter.inc(event.getDeposits().size());
    eth1DataCache.onBlockWithDeposit(
        event.getBlockNumber(),
        new Eth1Data(
            depositMerkleTree.getRoot(),
            UInt64.valueOf(depositMerkleTree.getDepositCount()),
            event.getBlockHash()),
        event.getBlockTimestamp());
  }

  @Override
  public void onNewFinalizedCheckpoint(
      final Checkpoint checkpoint, final boolean fromOptimisticBlock) {
    final BeaconState finalizedState = recentChainData.getStore().getLatestFinalized().getState();
    final UInt64 depositIndex = finalizedState.getEth1DepositIndex();
    pruneDeposits(depositIndex);
    synchronized (this) {
      if (depositIndex.isGreaterThanOrEqualTo(finalizedState.getEth1Data().getDepositCount())
          && depositMerkleTree.getDepositCount()
              >= finalizedState.getEth1Data().getDepositCount().longValue()) {
        final Optional<UInt64> heightOptional =
            eth1DataCache
                .getEth1DataAndHeight(finalizedState.getEth1Data())
                .map(Eth1DataCache.Eth1DataAndHeight::getBlockHeight);
        if (heightOptional.isEmpty()) {
          LOG.warn("Eth1Data height not found in cache. Skipping DepositTree finalization");
          return;
        }
        depositMerkleTree.finalize(finalizedState.getEth1Data(), heightOptional.get());
      }
    }
  }

  private synchronized void pruneDeposits(final UInt64 toIndex) {
    depositNavigableMap.headMap(toIndex, false).clear();
  }

  @Override
  public void onEth1Block(
      final UInt64 blockHeight, final Bytes32 blockHash, final UInt64 blockTimestamp) {
    eth1DataCache.onEth1Block(blockHeight, blockHash, blockTimestamp);
  }

  @Override
  public void onMinGenesisTimeBlock(MinGenesisTimeBlockEvent event) {}

  @Override
  public void onSlot(final UInt64 slot) {
    if (!inSync || !useMissingDepositEventLogging || recentChainData.getBestState().isEmpty()) {
      return;
    }

    // We want to verify our Beacon Node view of the eth1 deposits.
    // So we want to check if it has the necessary deposit data to propose a block

    recentChainData
        .getBestState()
        .get()
        .thenAccept(
            state -> {
              final UInt64 eth1DepositCount = state.getEth1Data().getDepositCount();

              final UInt64 lastAvailableDepositIndex =
                  depositNavigableMap.isEmpty()
                      ? state.getEth1DepositIndex()
                      : state.getEth1DepositIndex().max(depositNavigableMap.lastKey().plus(ONE));
              if (lastAvailableDepositIndex.isLessThan(eth1DepositCount)) {
                eventLogger.eth1DepositDataNotAvailable(
                    lastAvailableDepositIndex.plus(UInt64.ONE), eth1DepositCount);
              }
            })
        .ifExceptionGetsHereRaiseABug();
  }

  public void onSyncingStatusChanged(boolean inSync) {
    this.inSync = inSync;
  }

  public synchronized SszList<Deposit> getDeposits(BeaconState state, Eth1Data eth1Data) {
    final UInt64 eth1DepositCount;
    if (spec.isEnoughVotesToUpdateEth1Data(state, eth1Data, 1)) {
      eth1DepositCount = eth1Data.getDepositCount();
    } else {
      eth1DepositCount = state.getEth1Data().getDepositCount();
    }

    final UInt64 eth1DepositIndex = state.getEth1DepositIndex();

    // We need to have all the deposits that can be included in the state available to ensure
    // the generated proofs are valid
    checkRequiredDepositsAvailable(eth1DepositCount, eth1DepositIndex);

    final long maxDeposits = spec.getMaxDeposits(state);
    final UInt64 latestDepositIndexWithMaxBlock = eth1DepositIndex.plus(spec.getMaxDeposits(state));

    final UInt64 toDepositIndex =
        latestDepositIndexWithMaxBlock.isGreaterThan(eth1DepositCount)
            ? eth1DepositCount
            : latestDepositIndexWithMaxBlock;

    return getDepositsWithProof(eth1DepositIndex, toDepositIndex, eth1DepositCount, maxDeposits);
  }

  protected synchronized List<DepositWithIndex> getAvailableDeposits() {
    return new ArrayList<>(depositNavigableMap.values());
  }

  protected synchronized Optional<DepositTreeSnapshot> getFinalizedDepositTreeSnapshot() {
    return depositMerkleTree.getSnapshot();
  }

  private void checkRequiredDepositsAvailable(
      final UInt64 eth1DepositCount, final UInt64 eth1DepositIndex) {
    // Note that eth1_deposit_index in the state is actually the number of deposits
    // included, so always one bigger than the index of the last included deposit,
    // hence lastKey().plus(ONE).
    final UInt64 maxPossibleResultingDepositIndex =
        depositNavigableMap.isEmpty() ? eth1DepositIndex : depositNavigableMap.lastKey().plus(ONE);
    if (maxPossibleResultingDepositIndex.isLessThan(eth1DepositCount)) {
      throw MissingDepositsException.missingRange(
          maxPossibleResultingDepositIndex.plus(UInt64.ONE), eth1DepositCount);
    }
  }

  public synchronized int getDepositMapSize() {
    return depositNavigableMap.size();
  }

  /**
   * @param fromDepositIndex inclusive
   * @param toDepositIndex exclusive
   * @param eth1DepositCount number of deposits in the merkle tree according to Eth1Data in state
   */
  private SszList<Deposit> getDepositsWithProof(
      UInt64 fromDepositIndex, UInt64 toDepositIndex, UInt64 eth1DepositCount, long maxDeposits) {
    final AtomicReference<UInt64> expectedDepositIndex = new AtomicReference<>(fromDepositIndex);
    final SszListSchema<Deposit, ?> depositsSchema = depositsSchemaCache.get(maxDeposits);
    final SszBytes32VectorSchema<?> depositProofSchema = Deposit.SSZ_SCHEMA.getProofSchema();
    // No deposits to include so don't bother rewinding the merkle tree.
    if (fromDepositIndex.equals(toDepositIndex)) {
      return depositsSchema.createFromElements(emptyList());
    }
    if (depositMerkleTree.getDepositCount() < eth1DepositCount.intValue()) {
      throw MissingDepositsException.missingRange(
          UInt64.valueOf(depositMerkleTree.getDepositCount()), eth1DepositCount);
    }
    final DepositTree merkleTree =
        depositMerkleTree.getTreeAtDepositIndex(eth1DepositCount.intValue());
    return depositNavigableMap
        .subMap(fromDepositIndex, true, toDepositIndex, false)
        .values()
        .stream()
        .map(
            deposit -> {
              if (!deposit.getIndex().equals(expectedDepositIndex.get())) {
                throw MissingDepositsException.missingRange(
                    expectedDepositIndex.get(), deposit.getIndex());
              }
              expectedDepositIndex.set(deposit.getIndex().plus(ONE));
              SszBytes32Vector proof =
                  depositProofSchema.of(merkleTree.getProof(deposit.getIndex().intValue()));
              return new DepositWithIndex(proof, deposit.getData(), deposit.getIndex());
            })
        .collect(depositsSchema.collector());
  }

  @Override
  public synchronized void onInitialDepositTreeSnapshot(
      final DepositTreeSnapshot depositTreeSnapshot) {
    this.depositMerkleTree = DepositTree.fromSnapshot(depositTreeSnapshot);
  }

  private static class DepositsSchemaCache {
    private SszListSchema<Deposit, ?> cachedSchema;

    public SszListSchema<Deposit, ?> get(long maxDeposits) {
      SszListSchema<Deposit, ?> cachedSchemaLoc = cachedSchema;
      if (cachedSchemaLoc == null || maxDeposits != cachedSchemaLoc.getMaxLength()) {
        cachedSchemaLoc = SszListSchema.create(Deposit.SSZ_SCHEMA, maxDeposits);
        cachedSchema = cachedSchemaLoc;
      }
      return cachedSchemaLoc;
    }
  }
}
