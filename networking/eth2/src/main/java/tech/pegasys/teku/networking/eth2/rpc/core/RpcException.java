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

import static java.nio.charset.StandardCharsets.UTF_8;
import static tech.pegasys.teku.networking.eth2.rpc.core.RpcResponseStatus.HISTORICAL_DATA_UNAVAILABLE;
import static tech.pegasys.teku.networking.eth2.rpc.core.RpcResponseStatus.INVALID_REQUEST_CODE;
import static tech.pegasys.teku.networking.eth2.rpc.core.RpcResponseStatus.SERVER_ERROR_CODE;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;

public class RpcException extends Exception {
  private static final Logger LOG = LogManager.getLogger();

  public static final int MAXIMUM_ERROR_MESSAGE_LENGTH = 256;

  // Server errors
  public static class ServerErrorException extends RpcException {
    public ServerErrorException() {
      super(SERVER_ERROR_CODE, "Unexpected error");
    }
  }

  // Malformed data
  public static class DeserializationFailedException extends RpcException {
    public DeserializationFailedException() {
      super(INVALID_REQUEST_CODE, "Failed to deserialize payload");
    }
  }

  public static class DecompressFailedException extends RpcException {
    public DecompressFailedException() {
      super(INVALID_REQUEST_CODE, "Failed to uncompress message");
    }
  }

  // Unexpected message length
  public static class ExtraDataAppendedException extends RpcException {
    public ExtraDataAppendedException() {
      super(INVALID_REQUEST_CODE, "Extra data appended to end of message");
    }
  }

  public static class MessageTruncatedException extends RpcException {
    public MessageTruncatedException() {
      super(INVALID_REQUEST_CODE, "Message was truncated");
    }
  }

  public static class PayloadTruncatedException extends RpcException {
    public PayloadTruncatedException() {
      super(INVALID_REQUEST_CODE, "Message payload smaller than expected");
    }
  }

  // Constraint violation
  public static class ChunkTooLongException extends RpcException {
    public ChunkTooLongException() {
      super(INVALID_REQUEST_CODE, "Chunk exceeds maximum allowed length");
    }
  }

  // Custom errors
  public static class HistoricalDataUnavailableException extends RpcException {

    public HistoricalDataUnavailableException(final String errorMessage) {
      super(HISTORICAL_DATA_UNAVAILABLE, errorMessage);
    }
  }

  public static class LengthOutOfBoundsException extends RpcException {
    public LengthOutOfBoundsException() {
      super(INVALID_REQUEST_CODE, "Chunk length is not within bounds for expected type");
    }
  }

  private final byte responseCode;
  private final String errorMessage;

  public RpcException(final byte responseCode, final String errorMessage) {
    super("[Code " + ((int) responseCode) + "] " + errorMessage);
    this.responseCode = responseCode;
    this.errorMessage = errorMessage;
  }

  public RpcException(final byte responseCode, final RpcErrorMessage errorMessage) {
    this(responseCode, errorMessage.toString());
  }

  public byte getResponseCode() {
    return responseCode;
  }

  public String getErrorMessageString() {
    return errorMessage;
  }

  public RpcErrorMessage getErrorMessage() {
    Bytes bytes = Bytes.wrap(errorMessage.getBytes(UTF_8));
    if (bytes.size() > MAXIMUM_ERROR_MESSAGE_LENGTH) {
      LOG.debug("Message {} was longer than {} bytes", errorMessage, MAXIMUM_ERROR_MESSAGE_LENGTH);
      return new RpcErrorMessage(bytes.slice(0, MAXIMUM_ERROR_MESSAGE_LENGTH));
    }
    return new RpcErrorMessage(bytes);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final RpcException that = (RpcException) o;
    return responseCode == that.responseCode && Objects.equals(errorMessage, that.errorMessage);
  }

  @Override
  public int hashCode() {
    return Objects.hash(responseCode, errorMessage);
  }

  public static class RpcErrorMessage {
    private final Bytes data;

    public RpcErrorMessage(final Bytes data) {
      this.data = data;
    }

    public Bytes getData() {
      return data;
    }

    @Override
    public String toString() {
      try {
        return new String(data.toArray(), StandardCharsets.UTF_8);
      } catch (IllegalArgumentException ex) {
        LOG.trace("Error message could not be read as UTF-8: {} ", data);
        return data.toHexString().toLowerCase();
      }
    }
  }
}
