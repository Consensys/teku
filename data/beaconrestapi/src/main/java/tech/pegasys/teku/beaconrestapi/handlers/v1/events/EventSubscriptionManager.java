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

import static tech.pegasys.teku.beaconrestapi.RestApiConstants.TOPICS;

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
import tech.pegasys.teku.api.response.v1.ChainReorgEvent;
import tech.pegasys.teku.api.response.v1.EventType;
import tech.pegasys.teku.api.response.v1.FinalizedCheckpointEvent;
import tech.pegasys.teku.api.response.v1.HeadEvent;
import tech.pegasys.teku.api.response.v1.SyncStateChangeEvent;
import tech.pegasys.teku.api.schema.Attestation;
import tech.pegasys.teku.api.schema.SignedBeaconBlock;
import tech.pegasys.teku.api.schema.SignedVoluntaryExit;
import tech.pegasys.teku.beaconrestapi.ListQueryParameterUtils;
import tech.pegasys.teku.datastructures.attestation.ValidateableAttestation;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.provider.JsonProvider;
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
  // collection of subscribers
  private final Collection<EventSubscriber> eventSubscribers;

  public EventSubscriptionManager(
      final NodeDataProvider nodeDataProvider,
      final ChainDataProvider chainDataProvider,
      final JsonProvider jsonProvider,
      final SyncDataProvider syncDataProvider,
      final ConfigProvider configProvider,
      final AsyncRunner asyncRunner,
      final EventChannels eventChannels) {
    this.provider = chainDataProvider;
    this.jsonProvider = jsonProvider;
    this.asyncRunner = asyncRunner;
    this.eventSubscribers = new ConcurrentLinkedQueue<>();
    this.configProvider = configProvider;
    eventChannels.subscribe(ChainHeadChannel.class, this);
    eventChannels.subscribe(FinalizedCheckpointChannel.class, this);
    syncDataProvider.subscribeToSyncStateChanges(this::onSyncStateChange);
    nodeDataProvider.subscribeToReceivedBlocks(this::onNewBlock);
    nodeDataProvider.subscribeToValidAttestations(this::onNewAttestation);
    nodeDataProvider.subscribeToNewVoluntaryExits(this::onNewVoluntaryExit);
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
            asyncRunner);
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
          try {
            final String reorgEventString =
                jsonProvider.objectToJSON(
                    new ChainReorgEvent(
                        slot,
                        slot.minus(context.getCommonAncestorSlot()),
                        context.getOldBestBlockRoot(),
                        bestBlockRoot,
                        context.getOldBestStateRoot(),
                        stateRoot,
                        configProvider.computeEpochAtSlot(slot)));
            notifySubscribersOfEvent(EventType.chain_reorg, reorgEventString);
          } catch (JsonProcessingException ex) {
            LOG.error(ex);
          }
        });

    try {
      final String headEventString =
          jsonProvider.objectToJSON(
              new HeadEvent(
                  slot,
                  bestBlockRoot,
                  stateRoot,
                  epochTransition,
                  previousDutyDependentRoot,
                  currentDutyDependentRoot));
      notifySubscribersOfEvent(EventType.head, headEventString);
    } catch (JsonProcessingException ex) {
      LOG.error(ex);
    }
  }

  protected void onNewVoluntaryExit(
      final tech.pegasys.teku.datastructures.operations.SignedVoluntaryExit exit,
      final InternalValidationResult result) {
    try {
      final String newVoluntaryExitString =
          jsonProvider.objectToJSON(new SignedVoluntaryExit(exit));
      notifySubscribersOfEvent(EventType.voluntary_exit, newVoluntaryExitString);
    } catch (JsonProcessingException ex) {
      LOG.error(ex);
    }
  }

  protected void onNewAttestation(final ValidateableAttestation attestation) {
    try {
      final String newAttestationJsonString =
          jsonProvider.objectToJSON(new Attestation(attestation.getAttestation()));
      notifySubscribersOfEvent(EventType.attestation, newAttestationJsonString);
    } catch (JsonProcessingException ex) {
      LOG.error(ex);
    }
  }

  protected void onNewBlock(final tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock block) {
    try {
      final String newBlockJsonString = jsonProvider.objectToJSON(new SignedBeaconBlock(block));
      notifySubscribersOfEvent(EventType.block, newBlockJsonString);
    } catch (JsonProcessingException ex) {
      LOG.error(ex);
    }
  }

  @Override
  public void onNewFinalizedCheckpoint(final Checkpoint checkpoint) {
    try {
      Optional<Bytes32> stateRoot = provider.getStateRootFromBlockRoot(checkpoint.getRoot());
      final String checkpointString =
          jsonProvider.objectToJSON(
              new FinalizedCheckpointEvent(
                  checkpoint.getRoot(), stateRoot.orElse(Bytes32.ZERO), checkpoint.getEpoch()));
      notifySubscribersOfEvent(EventType.finalized_checkpoint, checkpointString);
    } catch (JsonProcessingException ex) {
      LOG.error(ex);
    }
  }

  protected void onSyncStateChange(final SyncState syncState) {
    try {
      final String newSyncStateString =
          jsonProvider.objectToJSON(new SyncStateChangeEvent(syncState.name()));
      notifySubscribersOfEvent(EventType.sync_state, newSyncStateString);
    } catch (JsonProcessingException ex) {
      LOG.error(ex);
    }
  }

  private void notifySubscribersOfEvent(final EventType eventType, final String eventString) {
    eventSubscribers.forEach(subscriber -> subscriber.onEvent(eventType, eventString));
  }
}
