/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.beaconrestapi.handlers.v1.events;

import static java.nio.charset.StandardCharsets.UTF_8;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TOPICS;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.sse.SseClient;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.ConfigProvider;
import tech.pegasys.teku.api.NodeDataProvider;
import tech.pegasys.teku.api.SyncDataProvider;
import tech.pegasys.teku.api.response.EventType;
import tech.pegasys.teku.beacon.sync.events.SyncState;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.restapi.endpoints.ListQueryParameterUtils;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SignedContributionAndProof;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.statetransition.block.ReceivedBlockEventsChannel;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceUpdatedResultSubscriber.ForkChoiceUpdatedResultNotification;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.storage.api.ChainHeadChannel;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;
import tech.pegasys.teku.storage.api.ReorgContext;

public class EventSubscriptionManager
    implements ChainHeadChannel, FinalizedCheckpointChannel, ReceivedBlockEventsChannel {
  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final ConfigProvider configProvider;
  private final ChainDataProvider provider;
  private final AsyncRunner asyncRunner;
  private final TimeProvider timeProvider;
  private final int maxPendingEvents;
  // collection of subscribers
  private final Collection<EventSubscriber> eventSubscribers;

  public EventSubscriptionManager(
      final Spec spec,
      final NodeDataProvider nodeDataProvider,
      final ChainDataProvider chainDataProvider,
      final SyncDataProvider syncDataProvider,
      final ConfigProvider configProvider,
      final AsyncRunner asyncRunner,
      final EventChannels eventChannels,
      final TimeProvider timeProvider,
      final int maxPendingEvents) {
    this.spec = spec;
    this.provider = chainDataProvider;
    this.asyncRunner = asyncRunner;
    this.timeProvider = timeProvider;
    this.maxPendingEvents = maxPendingEvents;
    this.eventSubscribers = new ConcurrentLinkedQueue<>();
    this.configProvider = configProvider;
    eventChannels.subscribe(ChainHeadChannel.class, this);
    eventChannels.subscribe(FinalizedCheckpointChannel.class, this);
    eventChannels.subscribe(ReceivedBlockEventsChannel.class, this);
    syncDataProvider.subscribeToSyncStateChanges(this::onSyncStateChange);
    nodeDataProvider.subscribeToReceivedBlobSidecar(this::onNewBlobSidecar);
    nodeDataProvider.subscribeToAttesterSlashing(this::onNewAttesterSlashing);
    nodeDataProvider.subscribeToProposerSlashing(this::onNewProposerSlashing);
    nodeDataProvider.subscribeToValidAttestations(this::onNewAttestation);
    nodeDataProvider.subscribeToNewVoluntaryExits(this::onNewVoluntaryExit);
    nodeDataProvider.subscribeToSyncCommitteeContributions(this::onSyncCommitteeContribution);
    nodeDataProvider.subscribeToNewBlsToExecutionChanges(this::onNewBlsToExecutionChange);
    nodeDataProvider.subscribeToForkChoiceUpdatedResult(this::onForkChoiceUpdatedResult);
    nodeDataProvider.subscribeToValidDataColumnSidecars(
        (dataColumnSidecar, remoteOrigin) -> onNewDataColumnSidecar(dataColumnSidecar));
  }

  public void registerClient(final SseClient sseClient) {
    LOG.trace("SSE client connected " + sseClient.hashCode());
    final List<String> allTopicsInContext =
        ListQueryParameterUtils.getParameterAsStringList(sseClient.ctx().queryParamMap(), TOPICS);
    final EventSubscriber subscriber =
        new EventSubscriber(
            allTopicsInContext,
            sseClient,
            () -> {
              eventSubscribers.removeIf(sub -> sub.getSseClient().equals(sseClient));
              LOG.trace("disconnected " + sseClient.hashCode());
            },
            asyncRunner,
            timeProvider,
            maxPendingEvents);
    eventSubscribers.add(subscriber);
    subscriber.sendReadyComment();
  }

  @Override
  public void chainHeadUpdated(
      final UInt64 slot,
      final Bytes32 stateRoot,
      final Bytes32 bestBlockRoot,
      final boolean epochTransition,
      final boolean executionOptimistic,
      final Bytes32 previousDutyDependentRoot,
      final Bytes32 currentDutyDependentRoot,
      final Optional<ReorgContext> optionalReorgContext) {

    optionalReorgContext.ifPresent(
        context -> {
          final ChainReorgEvent reorgEvent =
              new ChainReorgEvent(
                  slot,
                  slot.minus(context.getCommonAncestorSlot()),
                  context.getOldBestBlockRoot(),
                  bestBlockRoot,
                  context.getOldBestStateRoot(),
                  stateRoot,
                  configProvider.computeEpochAtSlot(slot),
                  executionOptimistic);
          notifySubscribersOfEvent(EventType.chain_reorg, reorgEvent);
        });

    final HeadEvent headEvent =
        new HeadEvent(
            slot,
            bestBlockRoot,
            stateRoot,
            epochTransition,
            executionOptimistic,
            previousDutyDependentRoot,
            currentDutyDependentRoot);
    notifySubscribersOfEvent(EventType.head, headEvent);
  }

  @Override
  public void onNewFinalizedCheckpoint(
      final Checkpoint checkpoint, final boolean fromOptimisticBlock) {
    Optional<Bytes32> stateRoot = provider.getStateRootFromBlockRoot(checkpoint.getRoot());
    final FinalizedCheckpointEvent event =
        new FinalizedCheckpointEvent(
            checkpoint.getRoot(),
            stateRoot.orElse(Bytes32.ZERO),
            checkpoint.getEpoch(),
            fromOptimisticBlock);
    notifySubscribersOfEvent(EventType.finalized_checkpoint, event);
  }

  @Override
  public void onBlockValidated(final SignedBeaconBlock block) {
    onNewBlockGossip(block);
  }

  @Override
  public void onBlockImported(final SignedBeaconBlock block, final boolean executionOptimistic) {
    onNewBlock(block, executionOptimistic);
  }

  protected void onNewVoluntaryExit(
      final SignedVoluntaryExit exit,
      final InternalValidationResult result,
      final boolean fromNetwork) {
    final VoluntaryExitEvent voluntaryExitEvent = new VoluntaryExitEvent(exit);
    notifySubscribersOfEvent(EventType.voluntary_exit, voluntaryExitEvent);
  }

  protected void onNewBlsToExecutionChange(
      final SignedBlsToExecutionChange blsToExecutionChange,
      final InternalValidationResult result,
      final boolean fromNetwork) {
    if (result.isAccept()) {
      final BlsToExecutionChangeEvent blsToExecutionChangeEvent =
          new BlsToExecutionChangeEvent(blsToExecutionChange);
      notifySubscribersOfEvent(EventType.bls_to_execution_change, blsToExecutionChangeEvent);
    }
  }

  protected void onSyncCommitteeContribution(
      final SignedContributionAndProof proof,
      final InternalValidationResult result,
      final boolean fromNetwork) {
    if (result.isAccept()) {
      final ContributionAndProofEvent signedContributionAndProof =
          new ContributionAndProofEvent(proof);
      notifySubscribersOfEvent(EventType.contribution_and_proof, signedContributionAndProof);
    }
  }

  protected void onNewAttestation(final ValidatableAttestation attestation) {
    final Attestation actualAttestation = attestation.getUnconvertedAttestation();
    if (!actualAttestation.isSingleAttestation()) {
      final AttestationEvent attestationEvent = new AttestationEvent(actualAttestation);
      notifySubscribersOfEvent(EventType.attestation, attestationEvent);
    } else {
      final SingleAttestationEvent attestationEvent =
          new SingleAttestationEvent(actualAttestation.toSingleAttestationRequired());
      notifySubscribersOfEvent(EventType.single_attestation, attestationEvent);
    }
  }

  protected void onNewBlock(final SignedBeaconBlock block, final boolean executionOptimistic) {
    final BlockEvent blockEvent = new BlockEvent(block, executionOptimistic);
    notifySubscribersOfEvent(EventType.block, blockEvent);
  }

  protected void onNewBlockGossip(final SignedBeaconBlock block) {
    final BlockGossipEvent blockGossipEvent = new BlockGossipEvent(block);
    notifySubscribersOfEvent(EventType.block_gossip, blockGossipEvent);
  }

  protected void onNewBlobSidecar(final BlobSidecar blobSidecar) {
    final BlobSidecarEvent blobSidecarEvent = BlobSidecarEvent.create(spec, blobSidecar);
    notifySubscribersOfEvent(EventType.blob_sidecar, blobSidecarEvent);
  }

  protected void onNewDataColumnSidecar(final DataColumnSidecar dataColumnSidecar) {
    final DataColumnSidecarEvent dataColumnSidecarEvent =
        DataColumnSidecarEvent.create(dataColumnSidecar);
    notifySubscribersOfEvent(EventType.data_column_sidecar, dataColumnSidecarEvent);
  }

  protected void onNewAttesterSlashing(
      final AttesterSlashing attesterSlashing,
      final InternalValidationResult result,
      final boolean fromNetwork) {
    if (result.isAccept()) {
      notifySubscribersOfEvent(
          EventType.attester_slashing, new AttesterSlashingEvent(attesterSlashing));
    }
  }

  protected void onNewProposerSlashing(
      final ProposerSlashing proposerSlashing,
      final InternalValidationResult result,
      final boolean fromNetwork) {
    if (result.isAccept()) {
      notifySubscribersOfEvent(
          EventType.proposer_slashing, new ProposerSlashingEvent(proposerSlashing));
    }
  }

  protected void onForkChoiceUpdatedResult(
      final ForkChoiceUpdatedResultNotification forkChoiceUpdatedResultNotification) {
    forkChoiceUpdatedResultNotification
        .payloadAttributes()
        .ifPresent(
            payloadAttributes -> {
              final SpecMilestone milestone =
                  spec.atSlot(payloadAttributes.getProposalSlot()).getMilestone();
              final PayloadAttributesEvent payloadAttributesEvent =
                  PayloadAttributesEvent.create(
                      milestone,
                      payloadAttributes,
                      forkChoiceUpdatedResultNotification.forkChoiceState());
              notifySubscribersOfEvent(EventType.payload_attributes, payloadAttributesEvent);
            });
  }

  protected void onSyncStateChange(final SyncState syncState) {
    notifySubscribersOfEvent(EventType.sync_state, new SyncStateChangeEvent(syncState.name()));
  }

  private void notifySubscribersOfEvent(final EventType eventType, final Event<?> event) {
    final EventSource<?> eventSource = new EventSource<>(event);
    try {
      for (EventSubscriber subscriber : eventSubscribers) {
        subscriber.onEvent(eventType, eventSource);
      }
    } catch (final JsonProcessingException e) {
      LOG.error("Failed to serialize event", e);
    }
  }

  public static class EventSource<T> {
    private final Event<T> event;
    private Bytes value;

    public EventSource(final Event<T> event) {
      this.event = event;
    }

    public Bytes get() throws JsonProcessingException {
      if (value == null) {
        value =
            Bytes.wrap(
                JsonUtil.serialize(event.getData(), event.getJsonTypeDefinition()).getBytes(UTF_8));
      }
      return value;
    }
  }
}
