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

package tech.pegasys.artemis.networking.eth2.rpc.core;

import java.io.IOException;
import java.io.InputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.RpcRequest;
import tech.pegasys.artemis.networking.eth2.rpc.core.encodings.RpcEncoding;

/**
 * A decoder responsible for handling a single rpc request
 *
 * @param <T> The type of request to expect
 */
public class RpcRequestDecoder<T extends RpcRequest> {
  private static final Logger LOG = LogManager.getLogger();

  private final Class<T> requestType;
  private final RpcEncoding encoding;

  public RpcRequestDecoder(final Class<T> requestType, final RpcEncoding encoding) {
    this.requestType = requestType;
    this.encoding = encoding;
  }

  public T decodeRequest(final InputStream input) throws RpcException {
    final T request = encoding.decodePayload(input, requestType);

    // Check for extra bytes remaining
    try {
      if (input.available() != 0) {
        throw RpcException.EXTRA_DATA_APPENDED;
      }
    } catch (IOException e) {
      // We were unable to check for extra bytes - log a warning and continue on
      LOG.warn(
          "Unexpected error encountered while checking bytes remaining in rpc input stream.", e);
    }

    return request;
  }
}
