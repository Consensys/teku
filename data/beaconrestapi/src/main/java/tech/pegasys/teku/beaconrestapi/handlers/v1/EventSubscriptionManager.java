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
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
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
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;
import tech.pegasys.teku.storage.api.ReorgEventChannel;

public class EventSubscriptionManager implements ReorgEventChannel, FinalizedCheckpointChannel {
  private static final Logger LOG = LogManager.getLogger();

  private static final String CHAIN_REORG = "chain_reorg";
  private static final String HEAD = "head";
  private static final String BLOCK = "block";
  private static final String ATTESTATION = "attestation";
  private static final String VOLUNTARY_EXIT = "voluntary_exit";
  private static final String FINALIZED_CHECKPOINT = "finalized_checkpoint";
  private final JsonProvider jsonProvider;
  private final ChainDataProvider provider;
  private final ConcurrentLinkedQueue<SseClient> clients = new ConcurrentLinkedQueue<>();

  public EventSubscriptionManager(
      final ChainDataProvider provider,
      final JsonProvider jsonProvider,
      final EventChannels eventChannels) {
    this.provider = provider;
    this.jsonProvider = jsonProvider;
    eventChannels.subscribe(ReorgEventChannel.class, this);
    eventChannels.subscribe(FinalizedCheckpointChannel.class, this);
  }

  public static final List<String> VALID_EVENT_TYPES =
      List.of(CHAIN_REORG, HEAD, BLOCK, ATTESTATION, VOLUNTARY_EXIT, FINALIZED_CHECKPOINT);

  public void registerClient(final SseClient sseClient) {
    LOG.trace("connected " + sseClient.hashCode());
    sseClient.onClose(
        () -> {
          clients.remove(sseClient);
          LOG.trace("disconnected " + sseClient.hashCode());
        });
    clients.add(sseClient);
  }

  @Override
  public void reorgOccurred(
      final Bytes32 bestBlockRoot,
      final UInt64 bestSlot,
      final Bytes32 bestStateRoot,
      final Bytes32 oldBestBlockRoot,
      final Bytes32 oldBestStateRoot,
      final UInt64 commonAncestorSlot) {
    try {
      final UInt64 epoch = compute_epoch_at_slot(bestSlot);

      final String reorgEventString =
          jsonProvider.objectToJSON(
              new ChainReorgEvent(
                  bestSlot,
                  bestSlot.minus(commonAncestorSlot),
                  oldBestBlockRoot,
                  bestBlockRoot,
                  oldBestStateRoot,
                  bestStateRoot,
                  epoch));
      sendEventToClients(CHAIN_REORG, reorgEventString);
    } catch (JsonProcessingException ex) {
      LOG.trace(ex);
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
      sendEventToClients(FINALIZED_CHECKPOINT, checkpointString);
    } catch (JsonProcessingException ex) {
      LOG.trace(ex);
    }
  }

  public void stop() {
    clients.forEach(client -> client.sendEvent("bye", "bye", "-1"));
    clients.clear();
  }

  private void sendEventToClients(final String eventType, final String eventString) {
    clients.stream()
        .filter(client -> isSubscribedToTopic(eventType, client))
        .forEach(ctx -> ctx.sendEvent(eventType, eventString));
  }

  private boolean isSubscribedToTopic(final String topic, final SseClient sseClient) {
    List<String> subscribedTopics =
        ListQueryParameterUtils.getParameterAsStringList(sseClient.ctx.queryParamMap(), TOPICS);
    return subscribedTopics.contains(topic);
  }
}
