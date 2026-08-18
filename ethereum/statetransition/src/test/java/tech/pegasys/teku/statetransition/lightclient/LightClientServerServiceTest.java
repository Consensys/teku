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
import static org.assertj.core.api.Assertions.fail;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
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
import tech.pegasys.teku.spec.util.DataStructureUtil;

@TestSpecContext(allMilestones = true, ignoredMilestones = SpecMilestone.PHASE0)
public class LightClientServerServiceTest {

  private final Map<Bytes32, SignedBeaconBlock> blocksByRoot = new HashMap<>();
  private final Map<Bytes32, BeaconState> statesByRoot = new HashMap<>();

  private static final int MAX_RETAINED_PERIODS = 128;

  private Spec spec;
  private DataStructureUtil dataStructureUtil;
  private LightClientUpdateStore store;
  private LightClientServerService service;

  @BeforeEach
  void setUp(final SpecContext specContext) {
    spec = specContext.getSpec();
    dataStructureUtil = specContext.getDataStructureUtil();
    store = new LightClientUpdateStore(spec);
    service = new LightClientServerService(spec, store, this::lookUpBlock, this::lookUpState);
  }

  @TestTemplate
  public void onBlockImported_shouldIgnoreOptimisticBlocks() {
    final Chain chain = generateChain();

    serviceRejectingStateLookups().onBlockImported(chain.signature().getBlock(), true);

    assertNothingStored();
  }

  @TestTemplate
  public void onBlockImported_shouldIgnoreBlockWithInsufficientSyncCommitteeParticipants() {
    final int belowMinimum =
        SpecConfigAltair.required(spec.getGenesisSpecConfig()).getMinSyncCommitteeParticipants()
            - 1;
    final Chain chain =
        generateChain(
            BlockOptions.create()
                .setSyncAggregate(
                    dataStructureUtil.randomSyncAggregate(
                        IntStream.range(0, belowMinimum).toArray()))
                .setSkipStateTransition(true));

    serviceRejectingStateLookups().onBlockImported(chain.signature().getBlock(), false);

    assertNothingStored();
  }

  @TestTemplate
  public void onBlockImported_shouldNotStoreUpdateWhenPostStateIsUnavailable() {
    final Chain chain = generateChain();
    statesByRoot.remove(chain.signature().getRoot());

    service.onBlockImported(chain.signature().getBlock(), false);

    assertNothingStored();
  }

  @TestTemplate
  public void onBlockImported_shouldNotStoreUpdateWhenAttestedBlockIsUnavailable() {
    final Chain chain = generateChain();
    blocksByRoot.remove(chain.attested().getRoot());

    service.onBlockImported(chain.signature().getBlock(), false);

    assertNothingStored();
  }

  @TestTemplate
  public void onBlockImported_shouldNotStoreUpdateWhenAttestedStateIsUnavailable() {
    final Chain chain = generateChain();
    statesByRoot.remove(chain.attested().getRoot());

    service.onBlockImported(chain.signature().getBlock(), false);

    assertNothingStored();
  }

  @TestTemplate
  public void onBlockImported_shouldNotStoreUpdateWhenFinalizedBlockIsUnavailable() {
    final Chain chain = generateChain();
    statesByRoot.put(chain.attested().getRoot(), dataStructureUtil.randomBeaconState());

    service.onBlockImported(chain.signature().getBlock(), false);

    assertNothingStored();
  }

  @TestTemplate
  public void onBlockImported_shouldNotPropagateFailureWhenLookupFails() {
    final Chain chain = generateChain();
    final LightClientServerService failingService =
        new LightClientServerService(
            spec,
            store,
            this::lookUpBlock,
            __ -> SafeFuture.failedFuture(new IllegalStateException("store is down")));

    failingService.onBlockImported(chain.signature().getBlock(), false);

    assertNothingStored();
  }

  @TestTemplate
  public void onBlockImported_shouldStoreUpdateFinalityUpdateAndOptimisticUpdate() {
    final Chain chain = generateChain();

    service.onBlockImported(chain.signature().getBlock(), false);

    final LightClientUpdate update = onlyUpdateAtPeriod(0);
    assertThat(update.getAttestedHeader().getBeacon().getSlot())
        .isEqualTo(chain.attested().getSlot());
    assertThat(update.getSignatureSlot().get()).isEqualTo(chain.signature().getSlot());

    assertThat(store.getLatestFinalityUpdate()).isPresent();
    assertThat(store.getLatestOptimisticUpdate()).isPresent();
  }

  @TestTemplate
  public void onBlockImported_shouldNotBlockWhileLookupsArePending() {
    final Chain chain = generateChain();
    final SafeFuture<Optional<BeaconState>> pendingAttestedState = new SafeFuture<>();
    final LightClientServerService pendingService =
        new LightClientServerService(
            spec,
            store,
            this::lookUpBlock,
            root ->
                root.equals(chain.attested().getRoot()) ? pendingAttestedState : lookUpState(root));

    pendingService.onBlockImported(chain.signature().getBlock(), false);

    assertNothingStored();

    pendingAttestedState.complete(Optional.of(chain.attested().getState()));

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

  private record Chain(SignedBlockAndState attested, SignedBlockAndState signature) {}

  private SafeFuture<Optional<SignedBeaconBlock>> lookUpBlock(final Bytes32 root) {
    return SafeFuture.completedFuture(Optional.ofNullable(blocksByRoot.get(root)));
  }

  private SafeFuture<Optional<BeaconState>> lookUpState(final Bytes32 root) {
    return SafeFuture.completedFuture(Optional.ofNullable(statesByRoot.get(root)));
  }

  private LightClientServerService serviceRejectingStateLookups() {
    return new LightClientServerService(
        spec, store, this::lookUpBlock, __ -> fail("should have returned before looking up state"));
  }

  private Chain generateChain() {
    final int syncCommitteeSize =
        SpecConfigAltair.required(spec.getGenesisSpecConfig()).getSyncCommitteeSize();
    return generateChain(
        BlockOptions.create()
            .setSyncAggregate(
                dataStructureUtil.randomSyncAggregate(
                    IntStream.range(0, syncCommitteeSize).toArray())));
  }

  private Chain generateChain(final BlockOptions signatureBlockOptions) {
    final ChainBuilder chainBuilder = ChainBuilder.create(spec);
    chainBuilder.generateGenesis();
    final Chain chain =
        new Chain(
            chainBuilder.generateNextBlock(),
            chainBuilder.generateNextBlock(signatureBlockOptions));

    blocksByRoot.put(chain.attested().getRoot(), chain.attested().getBlock());
    statesByRoot.put(chain.attested().getRoot(), chain.attested().getState());
    statesByRoot.put(chain.signature().getRoot(), chain.signature().getState());
    return chain;
  }

  private LightClientUpdate addUpdateAtPeriod(final long period) {
    final LightClientUpdate update =
        dataStructureUtil.createRandomLightClientUpdateBuilder(periodStartSlot(period)).build();
    store.addUpdate(update);
    return update;
  }

  private Checkpoint checkpointAtPeriod(final long period) {
    return new Checkpoint(
        spec.computeEpochAtSlot(periodStartSlot(period)), dataStructureUtil.randomBytes32());
  }

  private UInt64 periodStartSlot(final long period) {
    return spec.computeStartSlotAtEpoch(
            spec.getSyncCommitteeUtilRequired(UInt64.ZERO)
                .computeFirstEpochOfNextSyncCommitteePeriod(UInt64.ZERO))
        .times(period);
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
