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

import static tech.pegasys.teku.networking.eth2.rpc.core.RpcResponseStatus.SUCCESS_RESPONSE_CODE;

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;

public final class RpcEncoder {
  private final RpcEncoding encoding;

  public RpcEncoder(final RpcEncoding encoding) {
    this.encoding = encoding;
  }

  /**
   * Encodes a message into a RPC request
   *
   * @param request the payload of the request
   * @return the encoded RPC message
   */
  public <T> Bytes encodeRequest(T request) {
    return encoding.encodePayload(request);
  }

  public <T> Bytes encodeSuccessfulResponse(T response) {
    return Bytes.concatenate(Bytes.of(SUCCESS_RESPONSE_CODE), encoding.encodePayload(response));
  }

  public Bytes encodeErrorResponse(RpcException error) {
    return Bytes.concatenate(
        Bytes.of(error.getResponseCode()), encoding.encodePayload(error.getErrorMessage()));
  }
}
