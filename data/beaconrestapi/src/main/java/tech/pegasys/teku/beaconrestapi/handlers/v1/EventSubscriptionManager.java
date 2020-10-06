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

package tech.pegasys.teku.beaconrestapi.handlers.v1;

import static tech.pegasys.teku.beaconrestapi.RestApiConstants.TOPICS;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.sse.SseClient;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.response.v1.ChainReorgEvent;
import tech.pegasys.teku.api.response.v1.FinalizedCheckpointEvent;
import tech.pegasys.teku.beaconrestapi.ListQueryParameterUtils;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.storage.api.ChainHeadChannel;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;
import tech.pegasys.teku.storage.api.ReorgContext;

public class EventSubscriptionManager implements ChainHeadChannel, FinalizedCheckpointChannel {
  private static final Logger LOG = LogManager.getLogger();

  public enum EventType {
    head,
    block,
    attestation,
    voluntary_exit,
    finalized_checkpoint,
    chain_reorg
  }

  private final JsonProvider jsonProvider;
  private final ChainDataProvider provider;
  private final Map<EventType, Queue<SseClient>> eventClientMap;

  public EventSubscriptionManager(
      final ChainDataProvider provider,
      final JsonProvider jsonProvider,
      final EventChannels eventChannels) {
    this.provider = provider;
    this.jsonProvider = jsonProvider;
    eventClientMap =
        Map.of(
            EventType.head, new ConcurrentLinkedQueue<>(),
            EventType.block, new ConcurrentLinkedQueue<>(),
            EventType.attestation, new ConcurrentLinkedQueue<>(),
            EventType.voluntary_exit, new ConcurrentLinkedQueue<>(),
            EventType.finalized_checkpoint, new ConcurrentLinkedQueue<>(),
            EventType.chain_reorg, new ConcurrentLinkedQueue<>());
    eventChannels.subscribe(ChainHeadChannel.class, this);
    eventChannels.subscribe(FinalizedCheckpointChannel.class, this);
  }

  public void registerClient(final SseClient sseClient) {
    LOG.trace("connected " + sseClient.hashCode());
    final List<String> allTopicsInContext =
        ListQueryParameterUtils.getParameterAsStringList(sseClient.ctx.queryParamMap(), TOPICS);
    sseClient.onClose(
        () -> {
          getTopics(allTopicsInContext).forEach(e -> eventClientMap.get(e).remove(sseClient));
          LOG.trace("disconnected " + sseClient.hashCode());
        });

    getTopics(allTopicsInContext).forEach(e -> eventClientMap.get(e).add(sseClient));
  }

  @Override
  public void chainHeadUpdated(
      final UInt64 slot,
      final Bytes32 stateRoot,
      final Bytes32 bestBlockRoot,
      final boolean epochTransition,
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
            sendEventToClients(EventType.chain_reorg, reorgEventString);
          } catch (JsonProcessingException ex) {
            LOG.error(ex);
          }
        });
  }

  @Override
  public void onNewFinalizedCheckpoint(final Checkpoint checkpoint) {
    try {
      Optional<Bytes32> stateRoot = provider.getStateRootFromBlockRoot(checkpoint.getRoot());
      final String checkpointString =
          jsonProvider.objectToJSON(
              new FinalizedCheckpointEvent(
                  checkpoint.getRoot(), stateRoot.orElse(Bytes32.ZERO), checkpoint.getEpoch()));
      sendEventToClients(EventType.finalized_checkpoint, checkpointString);
    } catch (JsonProcessingException ex) {
      LOG.error(ex);
    }
  }

  private void sendEventToClients(final EventType eventType, final String eventString) {
    eventClientMap.get(eventType).forEach(ctx -> ctx.sendEvent(eventType.name(), eventString));
  }

  List<EventType> getTopics(List<String> topics) {
    return topics.stream().map(EventType::valueOf).collect(Collectors.toList());
  }
}
