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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.NodeDataProvider;
import tech.pegasys.teku.beacon.sync.events.SyncState;
import tech.pegasys.teku.beacon.sync.events.SyncStateProvider;
import tech.pegasys.teku.beacon.sync.events.SyncStateTracker;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.gossip.BlockAndBlobsSidecarGossipChannel;
import tech.pegasys.teku.networking.eth2.gossip.BlockGossipChannel;
import tech.pegasys.teku.networking.eth2.gossip.subnets.AttestationTopicSubscriber;
import tech.pegasys.teku.networking.eth2.gossip.subnets.SyncCommitteeSubscriptionManager;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool;
import tech.pegasys.teku.statetransition.attestation.AttestationManager;
import tech.pegasys.teku.statetransition.block.BlockImportChannel;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceTrigger;
import tech.pegasys.teku.statetransition.forkchoice.ProposersDataManager;
import tech.pegasys.teku.statetransition.synccommittee.SyncCommitteeContributionPool;
import tech.pegasys.teku.statetransition.synccommittee.SyncCommitteeMessagePool;
import tech.pegasys.teku.storage.client.ChainUpdater;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.server.StateStorageMode;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.validator.coordinator.performance.DefaultPerformanceTracker;

public class ValidatorApiHandlerIntegrationTest {

  // Use full storage system
  private final StorageSystem storageSystem =
      InMemoryStorageSystemBuilder.buildDefault(StateStorageMode.ARCHIVE);
  private final CombinedChainDataClient combinedChainDataClient =
      storageSystem.combinedChainDataClient();
  private final Spec spec = TestSpecFactory.createMinimalPhase0();

  // Other dependencies are mocked, but these can be updated as needed
  private final SyncStateProvider syncStateProvider = mock(SyncStateTracker.class);
  private final BlockFactory blockFactory = mock(BlockFactory.class);
  private final AggregatingAttestationPool attestationPool = mock(AggregatingAttestationPool.class);
  private final AttestationManager attestationManager = mock(AttestationManager.class);
  private final AttestationTopicSubscriber attestationTopicSubscriber =
      mock(AttestationTopicSubscriber.class);
  private final ActiveValidatorTracker activeValidatorTracker = mock(ActiveValidatorTracker.class);
  private final DefaultPerformanceTracker performanceTracker =
      mock(DefaultPerformanceTracker.class);
  private final BlockImportChannel blockImportChannel = mock(BlockImportChannel.class);
  private final BlockGossipChannel blockGossipChannel = mock(BlockGossipChannel.class);
  private final BlockAndBlobsSidecarGossipChannel blockAndBlobsSidecarGossipChannel =
      mock(BlockAndBlobsSidecarGossipChannel.class);
  private final ChainDataProvider chainDataProvider = mock(ChainDataProvider.class);
  private final NodeDataProvider nodeDataProvider = mock(NodeDataProvider.class);
  private final ForkChoiceTrigger forkChoiceTrigger = mock(ForkChoiceTrigger.class);
  private final ProposersDataManager proposersDataManager = mock(ProposersDataManager.class);

  private final ChainUpdater chainUpdater = storageSystem.chainUpdater();
  private final SyncCommitteeMessagePool syncCommitteeMessagePool =
      mock(SyncCommitteeMessagePool.class);
  private final SyncCommitteeContributionPool syncCommitteeContributionPool =
      mock(SyncCommitteeContributionPool.class);
  private final SyncCommitteeSubscriptionManager syncCommitteeSubscriptionManager =
      mock(SyncCommitteeSubscriptionManager.class);
  private final ValidatorApiHandler handler =
      new ValidatorApiHandler(
          chainDataProvider,
          nodeDataProvider,
          combinedChainDataClient,
          syncStateProvider,
          blockFactory,
          blockImportChannel,
          blockGossipChannel,
          blockAndBlobsSidecarGossipChannel,
          attestationPool,
          attestationManager,
          attestationTopicSubscriber,
          activeValidatorTracker,
          mock(DutyMetrics.class),
          performanceTracker,
          spec,
          forkChoiceTrigger,
          proposersDataManager,
          syncCommitteeMessagePool,
          syncCommitteeContributionPool,
          syncCommitteeSubscriptionManager);

  @BeforeEach
  public void setup() {
    when(syncStateProvider.getCurrentSyncState()).thenReturn(SyncState.IN_SYNC);
    when(forkChoiceTrigger.prepareForAttestationProduction(any())).thenReturn(SafeFuture.COMPLETE);
  }

  @Test
  public void createAttestationData_withRecentBlockAvailable() {
    final UInt64 targetEpoch = UInt64.valueOf(3);
    final UInt64 targetEpochStartSlot = spec.computeStartSlotAtEpoch(targetEpoch);
    final UInt64 targetSlot = targetEpochStartSlot.plus(2);

    final SignedBlockAndState genesis = chainUpdater.initializeGenesis();
    final Checkpoint genesisCheckpoint = genesis.getState().getFinalizedCheckpoint();

    SignedBlockAndState latestBlock = null;
    SignedBlockAndState epochBoundaryBlock = null;
    while (chainUpdater.getHeadSlot().compareTo(targetSlot) < 0) {
      latestBlock = chainUpdater.advanceChain();
      chainUpdater.updateBestBlock(latestBlock);
      if (latestBlock.getSlot().equals(targetEpochStartSlot)) {
        epochBoundaryBlock = latestBlock;
      }
    }
    assertThat(latestBlock).isNotNull();
    assertThat(epochBoundaryBlock).isNotNull();
    final Checkpoint expectedTarget = new Checkpoint(targetEpoch, epochBoundaryBlock.getRoot());

    final int committeeIndex = 0;
    final SafeFuture<Optional<AttestationData>> result =
        handler.createAttestationData(targetSlot, committeeIndex);
    assertThatSafeFuture(result).isCompletedWithNonEmptyOptional();
    final AttestationData attestation = result.join().orElseThrow();
    assertThat(attestation.getBeaconBlockRoot()).isEqualTo(latestBlock.getRoot());
    assertThat(attestation.getSource()).isEqualTo(genesisCheckpoint);
    assertThat(attestation.getTarget()).isEqualTo(expectedTarget);
  }

  @Test
  public void createUnsignedAttestation_withLatestBlockFromAnOldEpoch() {
    final UInt64 latestEpoch = UInt64.valueOf(2);
    final UInt64 latestSlot = spec.computeStartSlotAtEpoch(latestEpoch).plus(ONE);
    final UInt64 targetEpoch = UInt64.valueOf(latestEpoch.longValue() + 3);
    final UInt64 targetEpochStartSlot = spec.computeStartSlotAtEpoch(targetEpoch);
    final UInt64 targetSlot = targetEpochStartSlot.plus(2);

    final SignedBlockAndState genesis = chainUpdater.initializeGenesis();
    final Checkpoint genesisCheckpoint = genesis.getState().getFinalizedCheckpoint();

    SignedBlockAndState latestBlock = null;
    while (chainUpdater.getHeadSlot().compareTo(latestSlot) < 0) {
      latestBlock = chainUpdater.advanceChain();
      chainUpdater.updateBestBlock(latestBlock);
    }
    chainUpdater.setCurrentSlot(targetSlot);
    assertThat(latestBlock).isNotNull();
    final Checkpoint expectedTarget = new Checkpoint(targetEpoch, latestBlock.getRoot());

    final int committeeIndex = 0;
    final SafeFuture<Optional<AttestationData>> result =
        handler.createAttestationData(targetSlot, committeeIndex);
    assertThatSafeFuture(result).isCompletedWithNonEmptyOptional();
    final AttestationData attestation = result.join().orElseThrow();
    assertThat(attestation.getBeaconBlockRoot()).isEqualTo(latestBlock.getRoot());
    assertThat(attestation.getSource()).isEqualTo(genesisCheckpoint);
    assertThat(attestation.getTarget()).isEqualTo(expectedTarget);
  }
}
