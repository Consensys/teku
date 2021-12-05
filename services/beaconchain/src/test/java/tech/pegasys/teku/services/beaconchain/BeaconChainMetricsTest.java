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

package tech.pegasys.teku.services.beaconchain;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory.BEACON;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetwork;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.NodeSlot;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.PendingAttestation;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.phase0.BeaconStatePhase0;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.validator.coordinator.Eth1DataCache;

class BeaconChainMetricsTest {
  private static final UInt64 NODE_SLOT_VALUE = UInt64.valueOf(100L);
  private final Spec spec = TestSpecFactory.createMainnetPhase0();
  private final int slotsPerHistoricalRoot =
      spec.getGenesisSpecConfig().getSlotsPerHistoricalRoot();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private final Bytes32 root =
      Bytes32.fromHexString("0x760aa80a2c5cc1452a5301ecb176b366372d5f2218e0c24eFFFFFFFFFFFFFFFF");
  private final Bytes32 root2 =
      Bytes32.fromHexString("0x760aa80a2c5cc1452a5301ecb176b366372d5f2218e0c24eFFFFFFFFFFFFFF7F");
  private final Bytes32 root3 =
      Bytes32.fromHexString("0x760aa80a2c5cc1452a5301ecb176b366372d5f2218e0c24e0000000000000080");
  private BeaconStatePhase0 state =
      dataStructureUtil.stateBuilderPhase0().slot(NODE_SLOT_VALUE).build();
  private StateAndBlockSummary chainHead;

  private final NodeSlot nodeSlot = new NodeSlot(NODE_SLOT_VALUE);

  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private final RecentChainData preGenesisChainData = MemoryOnlyRecentChainData.create();
  private final Eth2P2PNetwork eth2P2PNetwork = mock(Eth2P2PNetwork.class);
  private final Eth1DataCache eth1DataCache = mock(Eth1DataCache.class);
  private final Checkpoint finalizedCheckpoint = dataStructureUtil.randomCheckpoint();
  private final Checkpoint currentJustifiedCheckpoint = dataStructureUtil.randomCheckpoint();
  private final Checkpoint previousJustifiedCheckpoint = dataStructureUtil.randomCheckpoint();

  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();
  private final BeaconChainMetrics beaconChainMetrics =
      new BeaconChainMetrics(
          spec, recentChainData, nodeSlot, metricsSystem, eth2P2PNetwork, eth1DataCache);

  @BeforeEach
  void setUp() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBlockAndState(state).getBlock();
    chainHead = mock(SignedBlockAndState.class);
    when(chainHead.getBeaconBlock()).thenReturn(block.getBeaconBlock());
    when(chainHead.getRoot()).thenReturn(block.getRoot());
    when(chainHead.getSlot()).thenAnswer(__ -> state.getSlot());
    when(chainHead.getState()).thenAnswer(__ -> state);
    when(recentChainData.getChainHead()).thenReturn(Optional.of(chainHead));

    // Update state
    List<Bytes32> blockRootsList =
        new ArrayList<>(Collections.nCopies(1000, dataStructureUtil.randomBytes32()));
    state =
        state
            .updated(
                s -> {
                  s.setFinalized_checkpoint(finalizedCheckpoint);
                  s.setCurrent_justified_checkpoint(currentJustifiedCheckpoint);
                  s.setPrevious_justified_checkpoint(previousJustifiedCheckpoint);
                  s.getBlock_roots().setAllElements(blockRootsList);
                })
            .toVersionPhase0()
            .orElseThrow();
  }

  private void setBlockRoots(List<Bytes32> newBlockRoots) {
    state =
        state
            .updated(s -> s.getBlock_roots().setAllElements(newBlockRoots))
            .toVersionPhase0()
            .orElseThrow();
  }

  @Test
  void getLongFromRoot_shouldParseNegativeOne() {
    assertThat(-1L).isEqualTo(BeaconChainMetrics.getLongFromRoot(root));
  }

  @Test
  void getLongFromRoot_shouldParseMaxLong() {
    assertThat(Long.MAX_VALUE).isEqualTo(BeaconChainMetrics.getLongFromRoot(root2));
  }

  @Test
  void getLongFromRoot_shouldParseMinLong() {
    assertThat(Long.MIN_VALUE).isEqualTo(BeaconChainMetrics.getLongFromRoot(root3));
  }

  @Test
  void getCurrentSlotValue_shouldReturnCurrentSlot() {
    assertThat(metricsSystem.getGauge(BEACON, "slot").getValue())
        .isEqualTo(NODE_SLOT_VALUE.longValue());
  }

  @Test
  void getHeadSlotValue_shouldSupplyValueWhenStoreIsPresent() {
    when(recentChainData.isPreGenesis()).thenReturn(false);
    when(recentChainData.getHeadSlot()).thenReturn(ONE);

    assertThat(metricsSystem.getGauge(BEACON, "head_slot").getValue()).isEqualTo(1L);
  }

  @Test
  void getHeadSlotValue_shouldReturnNotSetWhenStoreNotPresent() {
    when(recentChainData.isPreGenesis()).thenReturn(true);

    assertThat(metricsSystem.getGauge(BEACON, "head_slot").getValue()).isZero();
  }

  @Test
  void getFinalizedEpochValue_shouldReturnNotSetWhenStoreNotPresent() {
    when(recentChainData.isPreGenesis()).thenReturn(true);

    assertThat(metricsSystem.getGauge(BEACON, "finalized_epoch").getValue()).isZero();
  }

  @Test
  void getFinalizedEpochValue_shouldSupplyValueWhenStoreIsPresent() {
    when(recentChainData.isPreGenesis()).thenReturn(false);
    beaconChainMetrics.onSlot(NODE_SLOT_VALUE);
    assertThat(metricsSystem.getGauge(BEACON, "finalized_epoch").getValue())
        .isEqualTo(state.getFinalized_checkpoint().getEpoch().longValue());
  }

  @Test
  void getPeerCount_shouldSupplyValue() {
    when(eth2P2PNetwork.getPeerCount()).thenReturn(1);
    assertThat(metricsSystem.getGauge(BEACON, "peer_count").getValue()).isEqualTo(1);
  }

  @Test
  void getHeadRootValue_shouldReturnNotSetWhenStoreNotPresent() {
    when(recentChainData.isPreGenesis()).thenReturn(true);

    assertThat(metricsSystem.getGauge(BEACON, "head_root").getValue()).isZero();
  }

  @Test
  void getHeadRootValue_shouldReturnValueWhenStoreIsPresent() {
    when(recentChainData.isPreGenesis()).thenReturn(false);
    when(recentChainData.getBestBlockRoot()).thenReturn(Optional.of(root));

    assertThat(metricsSystem.getGauge(BEACON, "head_root").getValue()).isEqualTo(-1);
  }

  @Test
  void getFinalizedRootValue_shouldReturnNotSetWhenStoreNotPresent() {
    assertThat(preGenesisChainData.isPreGenesis()).isTrue(); // Sanity check

    assertThat(metricsSystem.getGauge(BEACON, "finalized_root").getValue()).isZero();
  }

  @Test
  void getFinalizedRootValue_shouldReturnValueWhenStoreIsPresent() {
    beaconChainMetrics.onSlot(NODE_SLOT_VALUE);

    assertThat(metricsSystem.getGauge(BEACON, "finalized_root").getValue())
        .isEqualTo(BeaconChainMetrics.getLongFromRoot(state.getFinalized_checkpoint().getRoot()));
  }

  @Test
  void getPreviousJustifiedEpochValue_shouldReturnNotSetWhenStoreNotPresent() {
    assertThat(preGenesisChainData.isPreGenesis()).isTrue(); // Sanity check

    assertThat(metricsSystem.getGauge(BEACON, "previous_justified_epoch").getValue()).isZero();
  }

  @Test
  void getPreviousJustifiedEpochValue_shouldSupplyValueWhenStoreIsPresent() {
    beaconChainMetrics.onSlot(NODE_SLOT_VALUE);

    assertThat(metricsSystem.getGauge(BEACON, "previous_justified_epoch").getValue())
        .isEqualTo(state.getPrevious_justified_checkpoint().getEpoch().longValue());
  }

  @Test
  void getPreviousJustifiedRootValue_shouldReturnNotSetWhenStoreNotPresent() {
    assertThat(preGenesisChainData.isPreGenesis()).isTrue(); // Sanity check

    assertThat(metricsSystem.getGauge(BEACON, "previous_justified_root").getValue()).isZero();
  }

  @Test
  void getPreviousJustifiedRootValue_shouldReturnValueWhenStoreIsPresent() {
    beaconChainMetrics.onSlot(NODE_SLOT_VALUE);

    assertThat(metricsSystem.getGauge(BEACON, "previous_justified_root").getValue())
        .isEqualTo(
            BeaconChainMetrics.getLongFromRoot(state.getPrevious_justified_checkpoint().getRoot()));
  }

  @Test
  void getJustifiedRootValue_shouldReturnNotSetWhenStoreNotPresent() {
    assertThat(preGenesisChainData.isPreGenesis()).isTrue(); // Sanity check

    assertThat(metricsSystem.getGauge(BEACON, "current_justified_root").getValue()).isZero();
  }

  @Test
  void getJustifiedRootValue_shouldReturnValueWhenStoreIsPresent() {
    beaconChainMetrics.onSlot(NODE_SLOT_VALUE);
    assertThat(metricsSystem.getGauge(BEACON, "current_justified_root").getValue())
        .isEqualTo(
            BeaconChainMetrics.getLongFromRoot(state.getCurrent_justified_checkpoint().getRoot()));
  }

  @Test
  void getJustifiedEpochValue_shouldReturnNotSetWhenStoreNotPresent() {
    when(recentChainData.isPreGenesis()).thenReturn(true);

    assertThat(metricsSystem.getGauge(BEACON, "current_justified_epoch").getValue()).isZero();
  }

  @Test
  void getJustifiedEpochValue_shouldReturnValueWhenStoreIsPresent() {
    beaconChainMetrics.onSlot(NODE_SLOT_VALUE);
    assertThat(metricsSystem.getGauge(BEACON, "current_justified_epoch").getValue())
        .isEqualTo(state.getCurrent_justified_checkpoint().getEpoch().longValue());
  }

  @Test
  void getCurrentEpochValue_shouldReturnValueWhenNodeSlotIsSet() {
    final UInt64 epochAtSlot = spec.computeEpochAtSlot(nodeSlot.getValue());
    assertThat(metricsSystem.getGauge(BEACON, "epoch").getValue())
        .isEqualTo(epochAtSlot.longValue());
  }

  @Test
  void onSlot_shouldUpdateEth1DataMetrics() {
    beaconChainMetrics.onSlot(NODE_SLOT_VALUE);
    verify(eth1DataCache).updateMetrics(state);
  }

  @Test
  void activeValidators_retrievesCorrectValue() {
    final UInt64 slotNumber = spec.computeStartSlotAtEpoch(UInt64.valueOf(13));
    final List<Validator> validators =
        List.of(
            validator(13, 15, false),
            validator(14, 15, false),
            validator(10, 12, false),
            validator(10, 15, true));

    withSlotCurrentEpochAttestationsAndValidators(slotNumber, Collections.emptyList(), validators);
    when(chainHead.getSlot()).thenReturn(slotNumber);

    beaconChainMetrics.onSlot(slotNumber);
    assertThat(metricsSystem.getGauge(BEACON, "current_active_validators").getValue()).isEqualTo(2);
    assertThat(metricsSystem.getGauge(BEACON, "previous_active_validators").getValue())
        .isEqualTo(1);
  }

  @Test
  void currentLiveValidators_treatSameBitIndexInDifferentSlotAsUnique() {
    final SszBitlist bitlist = bitlistOf(1, 3, 5, 7);
    final List<PendingAttestation> attestations =
        Stream.concat(createAttestations(13, 1, bitlist), createAttestations(14, 1, bitlist))
            .collect(toList());
    withCurrentEpochAttestations(attestations);

    beaconChainMetrics.onSlot(UInt64.valueOf(100));
    assertThat(metricsSystem.getGauge(BEACON, "current_live_validators").getValue()).isEqualTo(8);
  }

  @Test
  void currentLiveValidators_treatSameBitIndexInDifferentCommitteeAsUnique() {
    final SszBitlist bitlist = bitlistOf(1, 3, 5, 7);
    final List<PendingAttestation> attestations =
        Stream.concat(createAttestations(13, 1, bitlist), createAttestations(13, 2, bitlist))
            .collect(toList());
    withCurrentEpochAttestations(attestations);

    beaconChainMetrics.onSlot(UInt64.valueOf(100));
    assertThat(metricsSystem.getGauge(BEACON, "current_live_validators").getValue()).isEqualTo(8);
  }

  @Test
  void currentLiveValidators_treatSameBitIndexInSameSlotAsOneValidator() {
    final SszBitlist bitlist1 = bitlistOf(1, 3, 5, 7);
    final SszBitlist bitlist2 = bitlistOf(1, 2, 3, 4);
    withCurrentEpochAttestations(createAttestations(13, 1, bitlist1, bitlist2).collect(toList()));

    beaconChainMetrics.onSlot(UInt64.valueOf(100));
    assertThat(metricsSystem.getGauge(BEACON, "current_live_validators").getValue()).isEqualTo(6);
  }

  @Test
  void previousLiveValidators_treatSameBitIndexInDifferentSlotAsUnique() {
    final SszBitlist bitlist = bitlistOf(1, 3, 5, 7);
    final List<PendingAttestation> attestations =
        Stream.concat(createAttestations(13, 1, bitlist), createAttestations(14, 1, bitlist))
            .collect(toList());
    withPreviousEpochAttestations(100, attestations);

    beaconChainMetrics.onSlot(UInt64.valueOf(100));
    assertThat(metricsSystem.getGauge(BEACON, "previous_live_validators").getValue()).isEqualTo(8);
  }

  @Test
  void previousLiveValidators_treatSameBitIndexInDifferentCommitteeAsUnique() {
    final SszBitlist bitlist = bitlistOf(1, 3, 5, 7);
    final List<PendingAttestation> attestations =
        Stream.concat(createAttestations(13, 1, bitlist), createAttestations(13, 2, bitlist))
            .collect(toList());
    withPreviousEpochAttestations(100, attestations);

    beaconChainMetrics.onSlot(UInt64.valueOf(100));
    assertThat(metricsSystem.getGauge(BEACON, "previous_live_validators").getValue()).isEqualTo(8);
  }

  @Test
  void previousLiveValidators_treatSameBitIndexInSameSlotAsOneValidator() {
    final SszBitlist bitlist1 = bitlistOf(1, 3, 5, 7);
    final SszBitlist bitlist2 = bitlistOf(1, 2, 3, 4);
    withPreviousEpochAttestations(
        100, createAttestations(13, 1, bitlist1, bitlist2).collect(toList()));

    beaconChainMetrics.onSlot(UInt64.valueOf(100));
    assertThat(metricsSystem.getGauge(BEACON, "previous_live_validators").getValue()).isEqualTo(6);
  }

  @Test
  void currentCorrectValidators_onlyCountValidatorsWithCorrectTarget() {
    Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    Checkpoint target = new Checkpoint(spec.computeEpochAtSlot(UInt64.valueOf(13)), blockRoot);

    List<Bytes32> blockRootsList =
        new ArrayList<>(Collections.nCopies(33, dataStructureUtil.randomBytes32()));
    blockRootsList.set(
        target.getEpochStartSlot(spec).mod(slotsPerHistoricalRoot).intValue(), blockRoot);
    setBlockRoots(blockRootsList);
    final SszBitlist bitlist1 = bitlistOf(1, 3, 5, 7);
    final SszBitlist bitlist2 = bitlistOf(2, 4, 6, 8);
    List<PendingAttestation> allAttestations =
        Stream.concat(
                createAttestationsWithTargetCheckpoint(13, 1, target, bitlist1),
                createAttestationsWithTargetCheckpoint(
                    13,
                    1,
                    new Checkpoint(spec.computeEpochAtSlot(UInt64.valueOf(13)), blockRoot.not()),
                    bitlist2))
            .collect(toList());

    withCurrentEpochAttestations(
        allAttestations, UInt64.valueOf(15), dataStructureUtil.randomBytes32());

    beaconChainMetrics.onSlot(UInt64.valueOf(20));
    assertThat(metricsSystem.getGauge(BEACON, "current_correct_validators").getValue())
        .isEqualTo(4);
  }

  @Test
  void currentCorrectValidators_withStateAtFirstSlotOfEpoch() {
    Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    final UInt64 slot = spec.computeStartSlotAtEpoch(UInt64.ONE);
    Checkpoint target = new Checkpoint(spec.computeEpochAtSlot(slot), blockRoot);

    List<Bytes32> blockRootsList =
        new ArrayList<>(Collections.nCopies(33, dataStructureUtil.randomBytes32()));
    blockRootsList.set(slot.mod(slotsPerHistoricalRoot).intValue(), blockRoot);
    setBlockRoots(blockRootsList);
    final SszBitlist bitlist1 = bitlistOf(1, 3, 5, 7);
    final SszBitlist bitlist2 = bitlistOf(2, 4, 6, 8);
    List<PendingAttestation> allAttestations =
        Stream.concat(
                createAttestationsWithTargetCheckpoint(slot.intValue(), 1, target, bitlist1),
                createAttestationsWithTargetCheckpoint(
                    slot.intValue(),
                    1,
                    new Checkpoint(spec.computeEpochAtSlot(slot), blockRoot.not()),
                    bitlist2))
            .collect(toList());

    withCurrentEpochAttestations(allAttestations, slot, blockRoot);

    beaconChainMetrics.onSlot(UInt64.valueOf(20));
    assertThat(metricsSystem.getGauge(BEACON, "current_correct_validators").getValue())
        .isEqualTo(4);
  }

  @Test
  void previousCorrectValidators_onlyCountValidatorsWithCorrectTarget() {
    Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    Checkpoint target = new Checkpoint(spec.computeEpochAtSlot(UInt64.valueOf(13)), blockRoot);

    List<Bytes32> blockRootsList =
        new ArrayList<>(Collections.nCopies(33, dataStructureUtil.randomBytes32()));
    final int blockRootIndex =
        target.getEpochStartSlot(spec).mod(slotsPerHistoricalRoot).intValue();
    blockRootsList.set(blockRootIndex, blockRoot);
    setBlockRoots(blockRootsList);
    final SszBitlist bitlist1 = bitlistOf(1, 3, 5, 7);
    final SszBitlist bitlist2 = bitlistOf(2, 4, 6, 8);
    List<PendingAttestation> allAttestations =
        Stream.concat(
                createAttestationsWithTargetCheckpoint(13, 1, target, bitlist1),
                createAttestationsWithTargetCheckpoint(
                    15,
                    1,
                    new Checkpoint(spec.computeEpochAtSlot(UInt64.valueOf(13)), blockRoot.not()),
                    bitlist2))
            .collect(toList());

    final int slotInNextEpoch = spec.computeStartSlotAtEpoch(UInt64.ONE).plus(13).intValue();
    withPreviousEpochAttestations(slotInNextEpoch, allAttestations);

    beaconChainMetrics.onSlot(UInt64.valueOf(20));
    assertThat(metricsSystem.getGauge(BEACON, "previous_correct_validators").getValue())
        .isEqualTo(4);
  }

  private void withCurrentEpochAttestations(final List<PendingAttestation> attestations) {
    withCurrentEpochAttestations(
        attestations, UInt64.valueOf(100), dataStructureUtil.randomBytes32());
  }

  private void withCurrentEpochAttestations(
      final List<PendingAttestation> attestations,
      final UInt64 slot,
      final Bytes32 currentBlockRoot) {
    withSlotCurrentEpochAttestationsAndValidators(slot, attestations, Collections.emptyList());

    final StateAndBlockSummary stateAndBlock = mock(StateAndBlockSummary.class);
    when(stateAndBlock.getSlot()).thenReturn(slot);
    when(stateAndBlock.getState()).thenReturn(state);
    when(stateAndBlock.getRoot()).thenReturn(currentBlockRoot);
    when(recentChainData.getChainHead()).thenReturn(Optional.of(stateAndBlock));
  }

  private void withPreviousEpochAttestations(
      final int slotAsInt, final List<PendingAttestation> attestations) {
    state =
        state
            .updatedPhase0(
                s -> {
                  s.getPrevious_epoch_attestations().setAll(attestations);

                  s.getCurrent_epoch_attestations().clear();
                  s.setSlot(UInt64.valueOf(slotAsInt));
                })
            .toVersionPhase0()
            .orElseThrow();
  }

  private void withSlotCurrentEpochAttestationsAndValidators(
      final UInt64 slot,
      final List<PendingAttestation> attestations,
      final List<Validator> validatorsList) {
    state =
        state
            .updatedPhase0(
                s -> {
                  s.getCurrent_epoch_attestations().clear();
                  if (attestations.size() > 0) {
                    s.getCurrent_epoch_attestations().setAll(attestations);
                  }

                  s.getPrevious_epoch_attestations().clear();
                  s.setSlot(slot);

                  s.getValidators().clear();
                  if (validatorsList.size() > 0) {
                    s.getValidators().setAll(validatorsList);
                  }
                })
            .toVersionPhase0()
            .orElseThrow();
  }

  private Stream<PendingAttestation> createAttestations(
      final int slot, final int index, final SszBitlist... bitlists) {
    return Stream.of(bitlists)
        .map(
            bitlist1 ->
                new PendingAttestation(
                    bitlist1,
                    new AttestationData(
                        UInt64.valueOf(slot),
                        UInt64.valueOf(index),
                        dataStructureUtil.randomBytes32(),
                        dataStructureUtil.randomCheckpoint(),
                        dataStructureUtil.randomCheckpoint()),
                    dataStructureUtil.randomUInt64(),
                    dataStructureUtil.randomUInt64()));
  }

  private Stream<PendingAttestation> createAttestationsWithTargetCheckpoint(
      final int slot, final int index, final Checkpoint target, final SszBitlist... bitlists) {
    return Stream.of(bitlists)
        .map(
            bitlist1 ->
                new PendingAttestation(
                    bitlist1,
                    new AttestationData(
                        UInt64.valueOf(slot),
                        UInt64.valueOf(index),
                        dataStructureUtil.randomBytes32(),
                        dataStructureUtil.randomCheckpoint(),
                        target),
                    dataStructureUtil.randomUInt64(),
                    dataStructureUtil.randomUInt64()));
  }

  private SszBitlist bitlistOf(final int... indices) {
    return Attestation.SSZ_SCHEMA.getAggregationBitsSchema().ofBits(10, indices);
  }

  private Validator validator(
      final long activationEpoch, final long exitEpoch, final boolean slashed) {
    return new Validator(
        dataStructureUtil.randomPublicKeyBytes(),
        dataStructureUtil.randomBytes32(),
        dataStructureUtil.randomUInt64(),
        slashed,
        UInt64.valueOf(activationEpoch),
        UInt64.valueOf(activationEpoch),
        UInt64.valueOf(exitEpoch),
        UInt64.valueOf(exitEpoch));
  }
}
