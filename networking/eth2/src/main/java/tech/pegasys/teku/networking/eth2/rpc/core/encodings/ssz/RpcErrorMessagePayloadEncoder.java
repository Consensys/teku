/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.networking.eth2.rpc.core.encodings.ssz;

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.RpcErrorMessage;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcPayloadEncoder;

public class RpcErrorMessagePayloadEncoder implements RpcPayloadEncoder<RpcErrorMessage> {

  @Override
  public Bytes encode(final RpcErrorMessage message) {
    return message.getData();
  }

  @Override
  public RpcErrorMessage decode(final Bytes message) {
    return new RpcErrorMessage(message);
  }

  @Override
  public boolean isLengthWithinBounds(final long length) {
    return length <= RpcException.MAXIMUM_ERROR_MESSAGE_LENGTH;
  }
}
