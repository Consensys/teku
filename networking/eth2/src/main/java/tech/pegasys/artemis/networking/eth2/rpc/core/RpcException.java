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

import static tech.pegasys.artemis.networking.eth2.rpc.core.RpcResponseStatus.INVALID_REQUEST_CODE;
import static tech.pegasys.artemis.networking.eth2.rpc.core.RpcResponseStatus.SERVER_ERROR_CODE;

import java.util.Objects;

public class RpcException extends Exception {

  // Server errors
  public static final RpcException SERVER_ERROR =
      new RpcException(SERVER_ERROR_CODE, "Unexpected error");

  // Malformed data
  public static final RpcException DESERIALIZATION_FAILED =
      new RpcException(INVALID_REQUEST_CODE, "Failed to deserialize payload");
  public static final RpcException MALFORMED_MESSAGE_LENGTH =
      new RpcException(INVALID_REQUEST_CODE, "Message length was invalid");
  public static final RpcException FAILED_TO_UNCOMPRESS_MESSAGE =
      new RpcException(INVALID_REQUEST_CODE, "Failed to uncompress message");

  // Unexpected message length
  public static final RpcException EXTRA_DATA_APPENDED =
      new RpcException(INVALID_REQUEST_CODE, "Extra data appended to end of message");
  public static final RpcException MESSAGE_TRUNCATED =
      new RpcException(INVALID_REQUEST_CODE, "Message was truncated");
  public static final RpcException PAYLOAD_TRUNCATED =
      new RpcException(INVALID_REQUEST_CODE, "Message payload smaller than expected");

  // Constraint violation
  public static final RpcException CHUNK_TOO_LONG =
      new RpcException(INVALID_REQUEST_CODE, "Chunk exceeds maximum allowed length");

  private final byte responseCode;
  private final String errorMessage;

  public RpcException(final byte responseCode, final String errorMessage) {
    super("[Code " + ((int) responseCode) + "] " + errorMessage);
    this.responseCode = responseCode;
    this.errorMessage = errorMessage;
  }

  public byte getResponseCode() {
    return responseCode;
  }

  public String getErrorMessage() {
    return errorMessage;
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
}
