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
import java.util.concurrent.ConcurrentLinkedQueue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.response.v1.ChainReorgEvent;
import tech.pegasys.teku.beaconrestapi.ListQueryParameterUtils;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.storage.api.ReorgEventChannel;
import tech.pegasys.teku.util.time.channels.SlotEventsChannel;

public class EventSubscriptionService implements ReorgEventChannel, SlotEventsChannel {
  private static final Logger LOG = LogManager.getLogger();

  private static final String SLOT = "slot";
  private static final String CHAIN_REORG = "chain_reorg";
  private final JsonProvider jsonProvider;
  private final ConcurrentLinkedQueue<SseClient> clients = new ConcurrentLinkedQueue<>();

  public EventSubscriptionService(
      final JsonProvider jsonProvider, final EventChannels eventChannels) {
    this.jsonProvider = jsonProvider;
    eventChannels.subscribe(ReorgEventChannel.class, this);
    eventChannels.subscribe(SlotEventsChannel.class, this);
  }
  // FIXME remove slot
  public static final List<String> VALID_EVENT_TYPES =
      List.of(
          SLOT,
          CHAIN_REORG,
          "head",
          "block",
          "attestation",
          "voluntary_exit",
          "finalized_checkpoint");

  public void registerClient(final SseClient sseClient) {
    LOG.info("connected " + sseClient.hashCode());
    sseClient.onClose(
        () -> {
          clients.remove(sseClient);
          LOG.info("disconnected " + sseClient.hashCode());
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
  public void onSlot(final UInt64 slot) {
    sendEventToClients(SLOT, slot.toString());
  }

  public void stop() {
    clients.forEach(client -> client.sendEvent("bye", "bye", "-1"));
    LOG.info("Disconnected clients from GetEvents");
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
