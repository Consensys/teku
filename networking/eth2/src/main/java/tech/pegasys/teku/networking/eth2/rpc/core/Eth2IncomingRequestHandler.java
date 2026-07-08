/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.networking.eth2.rpc.core;

import static tech.pegasys.teku.networking.p2p.reputation.ReputationAdjustment.LARGE_PENALTY;

import com.google.common.annotations.VisibleForTesting;
import io.netty.buffer.ByteBuf;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.peers.PeerLookup;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.networking.p2p.rpc.RpcRequestHandler;
import tech.pegasys.teku.networking.p2p.rpc.RpcStream;
import tech.pegasys.teku.networking.p2p.rpc.StreamClosedException;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.RpcRequest;

public class Eth2IncomingRequestHandler<
        TRequest extends RpcRequest & SszData, TResponse extends SszData>
    implements RpcRequestHandler {

  private static final Logger LOG = LogManager.getLogger();

  private static final Duration RECEIVE_INCOMING_REQUEST_TIMEOUT = Duration.ofSeconds(10);

  private final PeerLookup peerLookup;
  private final LocalMessageHandler<TRequest, TResponse> localMessageHandler;
  private final RpcResponseEncoder<TResponse, ?> responseEncoder;

  private final RpcRequestDecoder<TRequest> requestDecoder;

  private final String protocolId;
  private final AsyncRunner asyncRunner;
  private final AtomicBoolean requestHandled = new AtomicBoolean(false);
  private Optional<Eth2Peer> cachedPeer = Optional.empty();

  public Eth2IncomingRequestHandler(
      final String protocolId,
      final RpcResponseEncoder<TResponse, ?> responseEncoder,
      final RpcRequestDecoder<TRequest> requestDecoder,
      final AsyncRunner asyncRunner,
      final PeerLookup peerLookup,
      final LocalMessageHandler<TRequest, TResponse> localMessageHandler) {
    this.protocolId = protocolId;
    this.asyncRunner = asyncRunner;
    this.peerLookup = peerLookup;
    this.localMessageHandler = localMessageHandler;
    this.responseEncoder = responseEncoder;
    this.requestDecoder = requestDecoder;
  }

  @Override
  public void active(final NodeId nodeId, final RpcStream rpcStream) {
    ensureRequestReceivedWithinTimeLimit(rpcStream);
  }

  @Override
  public void processData(final NodeId nodeId, final RpcStream rpcStream, final ByteBuf data) {
    final Optional<Eth2Peer> peer = getPeer(nodeId);
    final PenalizingResponseCallback<TResponse> responseCallback =
        createResponseCallback(rpcStream, peer);
    try {
      requestDecoder
          .decodeRequest(data)
          .ifPresent(request -> handleRequest(peer, request, responseCallback));
    } catch (final RpcException e) {
      requestHandled.set(true);
      responseCallback.completeWithErrorResponse(e);
    }
  }

  @Override
  public void readComplete(final NodeId nodeId, final RpcStream rpcStream) {
    final Optional<Eth2Peer> peer = getPeer(nodeId);
    final PenalizingResponseCallback<TResponse> responseCallback =
        createResponseCallback(rpcStream, peer);
    try {
      requestDecoder
          .complete()
          .ifPresent(request -> handleRequest(peer, request, responseCallback));
    } catch (RpcException e) {
      requestHandled.set(true);
      responseCallback.completeWithErrorResponse(e);
      LOG.debug("RPC Request stream closed prematurely {}", protocolId, e);
    }
  }

  @Override
  public void closed(final NodeId nodeId, final RpcStream rpcStream) {
    requestDecoder.close();
  }

  private void handleRequest(
      final Optional<Eth2Peer> peer,
      final TRequest request,
      final PenalizingResponseCallback<TResponse> callback) {
    try {
      requestHandled.set(true);
      final Optional<RpcException> requestValidationError =
          localMessageHandler.validateRequest(protocolId, request);
      if (requestValidationError.isPresent()) {
        callback.completeWithErrorResponse(requestValidationError.get());
        return;
      }
      localMessageHandler.onIncomingMessage(protocolId, peer, request, callback);
    } catch (final StreamClosedException e) {
      LOG.trace("Stream closed before response sent for request {}", protocolId, e);
      callback.completeWithUnexpectedError(e);
    } catch (final Throwable t) {
      LOG.error("Unhandled error while processing request {}", protocolId, t);
      callback.penalizePeer(
          "unexpected RPC request handler exception: " + t.getClass().getSimpleName());
      callback.completeWithUnexpectedError(t);
    }
  }

  private Optional<Eth2Peer> getPeer(final NodeId nodeId) {
    if (cachedPeer.isPresent()) {
      return cachedPeer;
    }

    final Optional<Eth2Peer> peer = peerLookup.getConnectedPeer(nodeId);
    if (peer.isPresent()) {
      cachedPeer = peer;
    }
    return peer;
  }

  private void ensureRequestReceivedWithinTimeLimit(final RpcStream stream) {
    asyncRunner
        .runAfterDelay(
            () -> {
              if (!requestHandled.get()) {
                LOG.debug(
                    "Failed to receive incoming request data within {} sec for protocol {}. Close stream.",
                    RECEIVE_INCOMING_REQUEST_TIMEOUT.toSeconds(),
                    protocolId);
                stream.closeAbruptly().finishStackTrace();
              }
            },
            RECEIVE_INCOMING_REQUEST_TIMEOUT)
        .finishStackTrace();
  }

  @VisibleForTesting
  boolean hasRequestBeenReceived() {
    return requestHandled.get();
  }

  @Override
  public String toString() {
    return "Eth2IncomingRequestHandler{" + "protocol=" + protocolId + '}';
  }

  private PenalizingResponseCallback<TResponse> createResponseCallback(
      final RpcStream rpcStream, final Optional<Eth2Peer> peer) {
    return new PenalizingResponseCallback<>(
        new RpcResponseCallback<>(rpcStream, responseEncoder), peer, protocolId);
  }

  private static class PenalizingResponseCallback<T> implements ResponseCallback<T> {
    private final ResponseCallback<T> delegate;
    private final Optional<Eth2Peer> peer;
    private final String protocolId;
    private final AtomicBoolean penaltyApplied = new AtomicBoolean(false);

    private PenalizingResponseCallback(
        final ResponseCallback<T> delegate,
        final Optional<Eth2Peer> peer,
        final String protocolId) {
      this.delegate = delegate;
      this.peer = peer;
      this.protocolId = protocolId;
    }

    @Override
    public SafeFuture<Void> respond(final T data) {
      return delegate.respond(data);
    }

    @Override
    public void respondAndCompleteSuccessfully(final T data) {
      delegate.respondAndCompleteSuccessfully(data);
    }

    @Override
    public void completeSuccessfully() {
      delegate.completeSuccessfully();
    }

    @Override
    public void completeWithErrorResponse(final RpcException error) {
      if (isMalformedRequestError(error)) {
        penalizePeer("malformed RPC request: " + error.getErrorMessageString());
      }
      delegate.completeWithErrorResponse(error);
    }

    @Override
    public void completeWithUnexpectedError(final Throwable error) {
      delegate.completeWithUnexpectedError(error);
    }

    private boolean isMalformedRequestError(final RpcException error) {
      return error instanceof RpcException.ChunkTooLongException
          || error instanceof RpcException.DecompressFailedException
          || error instanceof RpcException.DeserializationFailedException
          || error instanceof RpcException.ExtraDataAppendedException
          || error instanceof RpcException.LengthOutOfBoundsException
          || error instanceof RpcException.MessageTruncatedException
          || error instanceof RpcException.PayloadTruncatedException;
    }

    private void penalizePeer(final String reason) {
      if (penaltyApplied.compareAndSet(false, true)) {
        peer.ifPresent(
            p -> {
              LOG.debug("Penalising peer {} for {} on protocol {}", p.getId(), reason, protocolId);
              p.adjustReputation(LARGE_PENALTY);
            });
      }
    }
  }
}
