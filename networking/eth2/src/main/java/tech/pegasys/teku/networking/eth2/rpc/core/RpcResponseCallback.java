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

package tech.pegasys.teku.networking.eth2.rpc.core;

import java.nio.channels.ClosedChannelException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.RootCauseExceptionHandler;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.ServerErrorException;
import tech.pegasys.teku.networking.p2p.peer.PeerDisconnectedException;
import tech.pegasys.teku.networking.p2p.rpc.RpcStream;
import tech.pegasys.teku.networking.p2p.rpc.StreamClosedException;

class RpcResponseCallback<TResponse extends SszData> implements ResponseCallback<TResponse> {
  private static final Logger LOG = LogManager.getLogger();
  private final RpcResponseEncoder<TResponse, ?> responseEncoder;
  private final RpcStream rpcStream;

  public RpcResponseCallback(
      final RpcStream rpcStream, final RpcResponseEncoder<TResponse, ?> responseEncoder) {
    this.rpcStream = rpcStream;
    this.responseEncoder = responseEncoder;
  }

  @Override
  public SafeFuture<Void> respond(final TResponse data) {
    return rpcStream.writeBytes(responseEncoder.encodeSuccessfulResponse(data));
  }

  @Override
  public void respondAndCompleteSuccessfully(TResponse data) {
    respond(data)
        .thenRun(this::completeSuccessfully)
        .finish(
            RootCauseExceptionHandler.builder()
                .addCatch(
                    ClosedChannelException.class,
                    err -> LOG.trace("Failed to write because channel was closed", err))
                .defaultCatch(err -> LOG.error("Failed to write req/resp response", err)));
  }

  @Override
  public void completeSuccessfully() {
    rpcStream.closeWriteStream().reportExceptions();
  }

  @Override
  public void completeWithErrorResponse(final RpcException error) {
    LOG.debug("Responding to RPC request with error: {}", error.getErrorMessageString());
    try {
      rpcStream.writeBytes(responseEncoder.encodeErrorResponse(error)).reportExceptions();
    } catch (StreamClosedException e) {
      LOG.debug(
          "Unable to send error message ({}) to peer, rpc stream already closed: {}",
          error,
          rpcStream);
    }
    rpcStream.closeWriteStream().reportExceptions();
  }

  @Override
  public void completeWithUnexpectedError(final Throwable error) {
    if (error instanceof PeerDisconnectedException) {
      LOG.trace("Not sending RPC response as peer has already disconnected");
      // But close the stream just to be completely sure we don't leak any resources.
      rpcStream.closeAbruptly().reportExceptions();
    } else {
      completeWithErrorResponse(new ServerErrorException());
    }
  }
}
