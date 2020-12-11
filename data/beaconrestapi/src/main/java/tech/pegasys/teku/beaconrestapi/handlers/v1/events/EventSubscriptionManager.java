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
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;

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
import tech.pegasys.teku.api.SyncDataProvider;
import tech.pegasys.teku.api.response.v1.ChainReorgEvent;
import tech.pegasys.teku.api.response.v1.EventType;
import tech.pegasys.teku.api.response.v1.FinalizedCheckpointEvent;
import tech.pegasys.teku.api.response.v1.HeadEvent;
import tech.pegasys.teku.api.response.v1.SyncStateChangeEvent;
import tech.pegasys.teku.beaconrestapi.ListQueryParameterUtils;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.storage.api.ChainHeadChannel;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;
import tech.pegasys.teku.storage.api.ReorgContext;
import tech.pegasys.teku.sync.events.SyncState;

public class EventSubscriptionManager implements ChainHeadChannel, FinalizedCheckpointChannel {
  private static final Logger LOG = LogManager.getLogger();

  private final JsonProvider jsonProvider;
  private final ChainDataProvider provider;
  private final AsyncRunner asyncRunner;
  // collection of subscribers
  private final Collection<EventSubscriber> eventSubscribers;

  public EventSubscriptionManager(
      final ChainDataProvider provider,
      final JsonProvider jsonProvider,
      final SyncDataProvider syncDataProvider,
      final AsyncRunner asyncRunner,
      final EventChannels eventChannels) {
    this.provider = provider;
    this.jsonProvider = jsonProvider;
    this.asyncRunner = asyncRunner;
    this.eventSubscribers = new ConcurrentLinkedQueue<>();
    eventChannels.subscribe(ChainHeadChannel.class, this);
    eventChannels.subscribe(FinalizedCheckpointChannel.class, this);
    syncDataProvider.subscribeToSyncStateChanges(this::onSyncStateChange);
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
                        compute_epoch_at_slot(slot)));
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

  public void onSyncStateChange(final SyncState syncState) {
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
