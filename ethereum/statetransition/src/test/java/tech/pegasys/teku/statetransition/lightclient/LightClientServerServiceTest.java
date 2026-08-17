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

package tech.pegasys.teku.statetransition.lightclient;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientUpdate;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.generator.ChainBuilder.BlockOptions;
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;
import tech.pegasys.teku.spec.util.DataStructureUtil;

@TestSpecContext(allMilestones = true, ignoredMilestones = SpecMilestone.PHASE0)
public class LightClientServerServiceTest {

  /** Mirrors LightClientServerService.MAX_RETAINED_PERIODS. */
  private static final int MAX_RETAINED_PERIODS = 128;

  private final Map<Bytes32, SignedBeaconBlock> blocksByRoot = new HashMap<>();
  private final Map<Bytes32, BeaconState> statesByRoot = new HashMap<>();
  private final AtomicInteger stateLookups = new AtomicInteger();

  private Spec spec;
  private DataStructureUtil dataStructureUtil;
  private LightClientUpdateStore store;
  private LightClientServerService service;
  private UInt64 periodOneStartSlot;
  private int syncCommitteeSize;

  private SignedBlockAndState attested;
  private SignedBlockAndState signature;

  @BeforeEach
  void setUp(final SpecContext specContext) {
    spec = specContext.getSpec();
    dataStructureUtil = specContext.getDataStructureUtil();
    store = new LightClientUpdateStore(spec);
    service = new LightClientServerService(spec, store, this::lookUpBlock, this::lookUpState);

    final SyncCommitteeUtil syncCommitteeUtil = spec.getSyncCommitteeUtilRequired(UInt64.ZERO);
    periodOneStartSlot =
        spec.computeStartSlotAtEpoch(
            syncCommitteeUtil.computeFirstEpochOfNextSyncCommitteePeriod(UInt64.ZERO));
    syncCommitteeSize =
        SpecConfigAltair.required(spec.getGenesisSpecConfig()).getSyncCommitteeSize();
  }

  @TestTemplate
  public void onBlockImported_shouldIgnoreOptimisticBlocks() {
    generateChain();

    service.onBlockImported(signature.getBlock(), true);

    assertNothingStored();
    assertThat(stateLookups).hasValue(0);
  }

  @TestTemplate
  public void onBlockImported_shouldIgnoreBlockWithInsufficientSyncCommitteeParticipants() {
    final int belowMinimum =
        SpecConfigAltair.required(spec.getGenesisSpecConfig()).getMinSyncCommitteeParticipants()
            - 1;
    generateChain(
        BlockOptions.create()
            .setSyncAggregate(
                dataStructureUtil.randomSyncAggregate(IntStream.range(0, belowMinimum).toArray()))
            // A sub-minimum aggregate cannot carry a valid signature, and the service must reject
            // it before anything looks at the state anyway.
            .setSkipStateTransition(true));

    service.onBlockImported(signature.getBlock(), false);

    assertNothingStored();
    assertThat(stateLookups).hasValue(0);
  }

  @TestTemplate
  public void onBlockImported_shouldNotStoreUpdateWhenPostStateIsUnavailable() {
    generateChain();
    statesByRoot.remove(signature.getRoot());

    service.onBlockImported(signature.getBlock(), false);

    assertNothingStored();
  }

  @TestTemplate
  public void onBlockImported_shouldNotStoreUpdateWhenAttestedBlockIsUnavailable() {
    generateChain();
    blocksByRoot.remove(attested.getRoot());

    service.onBlockImported(signature.getBlock(), false);

    assertNothingStored();
  }

  @TestTemplate
  public void onBlockImported_shouldNotStoreUpdateWhenAttestedStateIsUnavailable() {
    generateChain();
    statesByRoot.remove(attested.getRoot());

    service.onBlockImported(signature.getBlock(), false);

    assertNothingStored();
  }

  @TestTemplate
  public void onBlockImported_shouldNotStoreUpdateWhenFinalizedBlockIsUnavailable() {
    generateChain();
    // A state whose finalized checkpoint root is non-zero and points at a block we do not have.
    statesByRoot.put(attested.getRoot(), dataStructureUtil.randomBeaconState());

    service.onBlockImported(signature.getBlock(), false);

    assertNothingStored();
  }

  @TestTemplate
  public void onBlockImported_shouldNotPropagateFailureWhenLookupFails() {
    generateChain();
    final LightClientServerService failingService =
        new LightClientServerService(
            spec,
            store,
            this::lookUpBlock,
            root -> SafeFuture.failedFuture(new IllegalStateException("store is down")));

    failingService.onBlockImported(signature.getBlock(), false);

    assertNothingStored();
  }

  @TestTemplate
  public void onBlockImported_shouldStoreUpdateFinalityUpdateAndOptimisticUpdate() {
    generateChain();

    service.onBlockImported(signature.getBlock(), false);

    final LightClientUpdate update = onlyUpdateAtPeriod(0);
    assertThat(update.getAttestedHeader().getBeacon().getSlot()).isEqualTo(attested.getSlot());
    assertThat(update.getSignatureSlot().get()).isEqualTo(signature.getSlot());

    assertThat(store.getLatestFinalityUpdate()).isPresent();
    assertThat(store.getLatestOptimisticUpdate()).isPresent();
  }

  @TestTemplate
  public void onBlockImported_shouldNotBlockWhileLookupsArePending() {
    generateChain();
    final SafeFuture<Optional<BeaconState>> pendingAttestedState = new SafeFuture<>();
    final LightClientServerService pendingService =
        new LightClientServerService(
            spec,
            store,
            this::lookUpBlock,
            root -> root.equals(attested.getRoot()) ? pendingAttestedState : lookUpState(root));

    pendingService.onBlockImported(signature.getBlock(), false);

    // Returned without waiting on the outstanding lookup.
    assertNothingStored();

    pendingAttestedState.complete(Optional.of(attested.getState()));

    assertThat(store.getLatestOptimisticUpdate()).isPresent();
  }

  @TestTemplate
  public void onNewFinalizedCheckpoint_shouldPruneUpdatesBeyondRetentionWindow() {
    addUpdateAtPeriod(1);
    final LightClientUpdate retained = addUpdateAtPeriod(200);

    service.onNewFinalizedCheckpoint(checkpointAtPeriod(200), false);

    assertThat(store.getBestUpdatesInRange(UInt64.ONE, 1)).isEmpty();
    assertThat(onlyUpdateAtPeriod(200)).isEqualTo(retained);
  }

  @TestTemplate
  public void onNewFinalizedCheckpoint_shouldKeepUpdateExactlyAtRetentionBoundary() {
    final int boundaryPeriod = 200 - MAX_RETAINED_PERIODS;
    addUpdateAtPeriod(boundaryPeriod - 1);
    final LightClientUpdate atBoundary = addUpdateAtPeriod(boundaryPeriod);

    service.onNewFinalizedCheckpoint(checkpointAtPeriod(200), false);

    assertThat(store.getBestUpdatesInRange(UInt64.valueOf(boundaryPeriod - 1), 1)).isEmpty();
    assertThat(onlyUpdateAtPeriod(boundaryPeriod)).isEqualTo(atBoundary);
  }

  @TestTemplate
  public void onNewFinalizedCheckpoint_shouldNotPruneWhenFinalizedPeriodIsBelowRetention() {
    final LightClientUpdate update = addUpdateAtPeriod(1);

    service.onNewFinalizedCheckpoint(checkpointAtPeriod(5), false);

    assertThat(onlyUpdateAtPeriod(1)).isEqualTo(update);
  }

  private SafeFuture<Optional<SignedBeaconBlock>> lookUpBlock(final Bytes32 root) {
    return SafeFuture.completedFuture(Optional.ofNullable(blocksByRoot.get(root)));
  }

  private SafeFuture<Optional<BeaconState>> lookUpState(final Bytes32 root) {
    stateLookups.incrementAndGet();
    return SafeFuture.completedFuture(Optional.ofNullable(statesByRoot.get(root)));
  }

  /** Two real consecutive blocks, the second signed by the whole sync committee. */
  private void generateChain() {
    generateChain(
        BlockOptions.create()
            .setSyncAggregate(dataStructureUtil.randomSyncAggregate(fullParticipation())));
  }

  private void generateChain(final BlockOptions signatureBlockOptions) {
    final ChainBuilder chainBuilder = ChainBuilder.create(spec);
    chainBuilder.generateGenesis();
    attested = chainBuilder.generateNextBlock();
    signature = chainBuilder.generateNextBlock(signatureBlockOptions);

    blocksByRoot.put(attested.getRoot(), attested.getBlock());
    statesByRoot.put(attested.getRoot(), attested.getState());
    statesByRoot.put(signature.getRoot(), signature.getState());
    stateLookups.set(0);
  }

  private int[] fullParticipation() {
    return IntStream.range(0, syncCommitteeSize).toArray();
  }

  private LightClientUpdate addUpdateAtPeriod(final long period) {
    final LightClientUpdate update =
        dataStructureUtil
            .createRandomLightClientUpdateBuilder(periodOneStartSlot.times(period))
            .build();
    store.addUpdate(update);
    return update;
  }

  private Checkpoint checkpointAtPeriod(final long period) {
    return new Checkpoint(
        spec.computeEpochAtSlot(periodOneStartSlot.times(period)),
        dataStructureUtil.randomBytes32());
  }

  private LightClientUpdate onlyUpdateAtPeriod(final long period) {
    final var updates = store.getBestUpdatesInRange(UInt64.valueOf(period), 1);
    assertThat(updates).hasSize(1);
    return updates.getFirst();
  }

  private void assertNothingStored() {
    assertThat(store.getBestUpdatesInRange(UInt64.ZERO, 1)).isEmpty();
    assertThat(store.getLatestFinalityUpdate()).isEmpty();
    assertThat(store.getLatestOptimisticUpdate()).isEmpty();
  }
}
