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

package tech.pegasys.artemis.networking.p2p.jvmlibp2p.rpc;

import static com.google.common.base.Preconditions.checkArgument;

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.artemis.networking.p2p.jvmlibp2p.rpc.encodings.RpcEncoding;
import tech.pegasys.artemis.util.sos.SimpleOffsetSerializable;

public final class RpcCodec {
  public static final byte SUCCESS_RESPONSE_CODE = 0;
  private final RpcEncoding encoding;

  public RpcCodec(final RpcEncoding encoding) {
    this.encoding = encoding;
  }

  /**
   * Encodes a message into a RPC request
   *
   * @param request the payload of the request
   * @return the encoded RPC message
   */
  public <T extends SimpleOffsetSerializable> Bytes encodeRequest(T request) {
    return encoding.encodeMessage(request);
  }

  public <T extends SimpleOffsetSerializable> Bytes encodeSuccessfulResponse(T response) {
    return Bytes.concatenate(Bytes.of(SUCCESS_RESPONSE_CODE), encoding.encodeMessage(response));
  }

  public Bytes encodeErrorResponse(RpcException error) {
    return Bytes.concatenate(
        Bytes.of(error.getResponseCode()), encoding.encodeError(error.getErrorMessage()));
  }

  /**
   * Decodes a RPC request into a payload.
   *
   * @param message the bytes of the message to read
   * @return the payload, decoded
   */
  public <T> T decodeRequest(Bytes message, Class<T> clazz) throws RpcException {
    return encoding.decodeMessage(message, clazz);
  }

  public <T> T decodeResponse(Bytes message, Class<T> clazz) throws RpcException {
    checkArgument(!message.isEmpty(), "Cannot decode an empty response");
    final byte responseCode = message.get(0);
    if (responseCode == SUCCESS_RESPONSE_CODE) {
      return encoding.decodeMessage(message.slice(1), clazz);
    } else {
      throw new RpcException(responseCode, encoding.decodeError(message.slice(1)));
    }
  }
}
