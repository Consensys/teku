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

import static tech.pegasys.artemis.networking.eth2.rpc.core.RpcResponseStatus.SUCCESS_RESPONSE_CODE;
import static tech.pegasys.artemis.util.bytes.ByteUtil.toByteExactUnsigned;
import static tech.pegasys.artemis.util.iostreams.IOStreamConstants.END_OF_STREAM;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.OptionalInt;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.artemis.networking.eth2.rpc.core.encodings.RpcEncoding;

/**
 * Responsible for decoding a stream of responses to a single rpc request
 *
 * @param <T>
 */
public class RpcResponseDecoder<T> {
  private static final Logger LOG = LogManager.getLogger();

  private final Class<T> responseType;
  private final RpcEncoding encoding;

  protected RpcResponseDecoder(final Class<T> responseType, final RpcEncoding encoding) {
    this.responseType = responseType;
    this.encoding = encoding;
  }

  public Optional<T> decodeNextResponse(final InputStream input) throws RpcException {
    return decodeNextResponse(input, Optional.empty());
  }

  public Optional<T> decodeNextResponse(
      final InputStream input, final FirstByteReceivedListener firstByteReceivedListener)
      throws RpcException {
    return decodeNextResponse(input, Optional.of(firstByteReceivedListener));
  }

  private Optional<T> decodeNextResponse(
      final InputStream input, Optional<FirstByteReceivedListener> firstByteListener)
      throws RpcException {
    try {
      final OptionalInt maybeStatus = getNextStatusCode(input);
      if (maybeStatus.isEmpty()) {
        // Empty status indicates we're finished reading responses
        return Optional.empty();
      }
      firstByteListener.ifPresent(FirstByteReceivedListener::onFirstByteReceived);
      final int status = maybeStatus.getAsInt();
      if (status == SUCCESS_RESPONSE_CODE) {
        final T response = encoding.decodePayload(input, responseType);
        return Optional.of(response);
      } else {
        throw decodeError(input, status);
      }
    } catch (IOException e) {
      LOG.error("Unexpected error while reading rpc responses", e);
      throw RpcException.SERVER_ERROR;
    }
  }

  private RpcException decodeError(final InputStream input, final int statusCode)
      throws RpcException {
    final String errorMessage = encoding.decodePayload(input, String.class);
    return new RpcException(toByteExactUnsigned(statusCode), errorMessage);
  }

  private OptionalInt getNextStatusCode(final InputStream input) throws IOException {
    final int nextByte = input.read();
    if (nextByte == END_OF_STREAM) {
      return OptionalInt.empty();
    }

    return OptionalInt.of(nextByte);
  }

  public interface FirstByteReceivedListener {
    void onFirstByteReceived();
  }
}
