/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.artemis.networking.eth2.rpc.beaconchain.methods;

import static com.google.common.primitives.UnsignedLong.ONE;
import static com.google.common.primitives.UnsignedLong.ZERO;

import com.google.common.primitives.UnsignedLong;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.BeaconBlocksByRangeRequestMessage;
import tech.pegasys.artemis.networking.eth2.peers.Eth2Peer;
import tech.pegasys.artemis.networking.eth2.rpc.core.LocalMessageHandler;
import tech.pegasys.artemis.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.artemis.networking.eth2.rpc.core.RpcException;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.storage.Store;

public class BeaconBlocksByRangeMessageHandler
    implements LocalMessageHandler<BeaconBlocksByRangeRequestMessage, BeaconBlock> {
  private static final org.apache.logging.log4j.Logger LOG = LogManager.getLogger();

  private final ChainStorageClient storageClient;

  public BeaconBlocksByRangeMessageHandler(final ChainStorageClient storageClient) {
    this.storageClient = storageClient;
  }

  @Override
  public void onIncomingMessage(
      final Eth2Peer peer,
      final BeaconBlocksByRangeRequestMessage message,
      final ResponseCallback<BeaconBlock> callback) {
    LOG.trace(
        "Peer {} requested {} BeaconBlocks from chain {} starting at slot {} with step {}",
        peer.getId(),
        message.getHeadBlockRoot(),
        message.getStartSlot(),
        message.getCount(),
        message.getStep());
    try {
      if (message.getStep().compareTo(ONE) < 0) {
        callback.completeWithError(RpcException.INVALID_STEP);
        return;
      }
      sendMatchingBlocks(message, callback);
      callback.completeSuccessfully();
    } catch (final RpcException e) {
      callback.completeWithError(e);
    }
  }

  private void sendMatchingBlocks(
      final BeaconBlocksByRangeRequestMessage message, final ResponseCallback<BeaconBlock> callback)
      throws RpcException {
    final Store store = storageClient.getStore();
    if (store == null) {
      return;
    }
    if (!storageClient.isIncludedInBestState(message.getHeadBlockRoot())) {
      return;
    }
    final UnsignedLong bestSlot = storageClient.getBestSlot();
    if (bestSlot == null) {
      LOG.error("No best slot present despite having at least one canonical block");
      throw RpcException.SERVER_ERROR;
    }

    UnsignedLong remainingBlocks = message.getCount();
    for (UnsignedLong slot = message.getStartSlot();
        !remainingBlocks.equals(ZERO) && slot.compareTo(bestSlot) <= 0;
        slot = slot.plus(message.getStep())) {
      final Optional<BeaconBlock> maybeBlock = storageClient.getBlockBySlot(slot);
      if (maybeBlock.isPresent()) {
        remainingBlocks = remainingBlocks.minus(ONE);
        callback.respond(maybeBlock.get());
      }
    }
  }
}
