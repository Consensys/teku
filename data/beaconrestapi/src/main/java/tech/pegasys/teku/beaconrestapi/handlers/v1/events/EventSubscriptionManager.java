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

package tech.pegasys.teku.beaconrestapi.handlers.v1.events;

import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TOPICS;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.sse.SseClient;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.ConfigProvider;
import tech.pegasys.teku.api.NodeDataProvider;
import tech.pegasys.teku.api.SyncDataProvider;
import tech.pegasys.teku.api.response.v1.BlockEvent;
import tech.pegasys.teku.api.response.v1.ChainReorgEvent;
import tech.pegasys.teku.api.response.v1.EventType;
import tech.pegasys.teku.api.response.v1.FinalizedCheckpointEvent;
import tech.pegasys.teku.api.response.v1.HeadEvent;
import tech.pegasys.teku.api.response.v1.SyncStateChangeEvent;
import tech.pegasys.teku.api.schema.Attestation;
import tech.pegasys.teku.api.schema.SignedVoluntaryExit;
import tech.pegasys.teku.api.schema.altair.SignedContributionAndProof;
import tech.pegasys.teku.beaconrestapi.ListQueryParameterUtils;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.spec.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.storage.api.ChainHeadChannel;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;
import tech.pegasys.teku.storage.api.ReorgContext;
import tech.pegasys.teku.sync.events.SyncState;

public class EventSubscriptionManager implements ChainHeadChannel, FinalizedCheckpointChannel {
  private static final Logger LOG = LogManager.getLogger();

  private final ConfigProvider configProvider;
  private final JsonProvider jsonProvider;
  private final ChainDataProvider provider;
  private final AsyncRunner asyncRunner;
  private final int maxPendingEvents;
  // collection of subscribers
  private final Collection<EventSubscriber> eventSubscribers;

  public EventSubscriptionManager(
      final NodeDataProvider nodeDataProvider,
      final ChainDataProvider chainDataProvider,
      final JsonProvider jsonProvider,
      final SyncDataProvider syncDataProvider,
      final ConfigProvider configProvider,
      final AsyncRunner asyncRunner,
      final EventChannels eventChannels,
      final int maxPendingEvents) {
    this.provider = chainDataProvider;
    this.jsonProvider = jsonProvider;
    this.asyncRunner = asyncRunner;
    this.maxPendingEvents = maxPendingEvents;
    this.eventSubscribers = new ConcurrentLinkedQueue<>();
    this.configProvider = configProvider;
    eventChannels.subscribe(ChainHeadChannel.class, this);
    eventChannels.subscribe(FinalizedCheckpointChannel.class, this);
    syncDataProvider.subscribeToSyncStateChanges(this::onSyncStateChange);
    nodeDataProvider.subscribeToReceivedBlocks(this::onNewBlock);
    nodeDataProvider.subscribeToValidAttestations(this::onNewAttestation);
    nodeDataProvider.subscribeToNewVoluntaryExits(this::onNewVoluntaryExit);
    nodeDataProvider.subscribeToSyncCommitteeContributions(this::onSyncCommitteeContribution);
  }

  public void registerClient(final SseClient sseClient) {
    LOG.trace("connected " + sseClient.hashCode());
    final List<String> allTopicsInContext =
        ListQueryParameterUtils.getParameterAsStringList(sseClient.ctx.queryParamMap(), TOPICS);
    final EventSubscriber subscriber =
        new EventSubscriber(
            allTopicsInContext,
            sseClient,
            () -> {
              eventSubscribers.removeIf(sub -> sub.getSseClient().equals(sseClient));
              LOG.trace("disconnected " + sseClient.hashCode());
            },
            asyncRunner,
            maxPendingEvents);
    eventSubscribers.add(subscriber);
  }

  @Override
  public void chainHeadUpdated(
      final UInt64 slot,
      final Bytes32 stateRoot,
      final Bytes32 bestBlockRoot,
      final boolean epochTransition,
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
                  configProvider.computeEpochAtSlot(slot));
          notifySubscribersOfEvent(EventType.chain_reorg, reorgEvent);
        });

    final HeadEvent headEvent =
        new HeadEvent(
            slot,
            bestBlockRoot,
            stateRoot,
            epochTransition,
            previousDutyDependentRoot,
            currentDutyDependentRoot);
    notifySubscribersOfEvent(EventType.head, headEvent);
  }

  protected void onNewVoluntaryExit(
      final tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit exit,
      final InternalValidationResult result) {
    final SignedVoluntaryExit voluntaryExitEvent = new SignedVoluntaryExit(exit);
    notifySubscribersOfEvent(EventType.voluntary_exit, voluntaryExitEvent);
  }

  protected void onSyncCommitteeContribution(
      final tech.pegasys.teku.spec.datastructures.operations.versions.altair
              .SignedContributionAndProof
          proof,
      final InternalValidationResult result) {
    if (result.isAccept()) {
      final SignedContributionAndProof signedContributionAndProof =
          new SignedContributionAndProof(proof);
      notifySubscribersOfEvent(EventType.contribution_and_proof, signedContributionAndProof);
    }
  }

  protected void onNewAttestation(final ValidateableAttestation attestation) {
    final Attestation attestationEvent = new Attestation(attestation.getAttestation());
    notifySubscribersOfEvent(EventType.attestation, attestationEvent);
  }

  protected void onNewBlock(
      final tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock block) {
    final BlockEvent blockEvent = BlockEvent.fromSignedBeaconBlock(block);
    notifySubscribersOfEvent(EventType.block, blockEvent);
  }

  @Override
  public void onNewFinalizedCheckpoint(final Checkpoint checkpoint) {
    Optional<Bytes32> stateRoot = provider.getStateRootFromBlockRoot(checkpoint.getRoot());
    final FinalizedCheckpointEvent checkpointString =
        new FinalizedCheckpointEvent(
            checkpoint.getRoot(), stateRoot.orElse(Bytes32.ZERO), checkpoint.getEpoch());
    notifySubscribersOfEvent(EventType.finalized_checkpoint, checkpointString);
  }

  protected void onSyncStateChange(final SyncState syncState) {
    notifySubscribersOfEvent(EventType.sync_state, new SyncStateChangeEvent(syncState.name()));
  }

  private void notifySubscribersOfEvent(final EventType eventType, final Object event) {
    final EventSource eventSource = new EventSource(jsonProvider, event);
    try {
      for (EventSubscriber subscriber : eventSubscribers) {
        subscriber.onEvent(eventType, eventSource);
      }
    } catch (final JsonProcessingException e) {
      LOG.error("Failed to serialize event", e);
    }
  }

  public static class EventSource {
    private final JsonProvider jsonProvider;
    private final Object event;
    private String value;

    public EventSource(final JsonProvider jsonProvider, final Object event) {
      this.jsonProvider = jsonProvider;
      this.event = event;
    }

    public String get() throws JsonProcessingException {
      if (value == null) {
        value = jsonProvider.objectToJSON(event);
      }
      return value;
    }
  }
}
