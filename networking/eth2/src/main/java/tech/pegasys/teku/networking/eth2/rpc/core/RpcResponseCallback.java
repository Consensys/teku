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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.networking.p2p.peer.PeerDisconnectedException;
import tech.pegasys.teku.networking.p2p.rpc.RpcStream;
import tech.pegasys.teku.networking.p2p.rpc.StreamClosedException;

class RpcResponseCallback<TResponse> implements ResponseCallback<TResponse> {
  private static final Logger LOG = LogManager.getLogger();
  private final RpcEncoder rpcEncoder;
  private final RpcStream rpcStream;

  public RpcResponseCallback(final RpcStream rpcStream, final RpcEncoder rpcEncoder) {
    this.rpcStream = rpcStream;
    this.rpcEncoder = rpcEncoder;
  }

  @Override
  public void respond(final TResponse data) {
    rpcStream.writeBytes(rpcEncoder.encodeSuccessfulResponse(data)).reportExceptions();
  }

  @Override
  public void completeSuccessfully() {
    rpcStream.closeWriteStream().reportExceptions();
  }

  @Override
  public void completeWithErrorResponse(final RpcException error) {
    LOG.debug("Responding to RPC request with error: {}", error.getErrorMessage());
    try {
      rpcStream.writeBytes(rpcEncoder.encodeErrorResponse(error)).reportExceptions();
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
      rpcStream.close().reportExceptions();
    } else {
      completeWithErrorResponse(RpcException.SERVER_ERROR);
    }
  }
}
