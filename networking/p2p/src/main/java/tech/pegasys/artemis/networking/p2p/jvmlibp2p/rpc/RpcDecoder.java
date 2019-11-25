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

import io.netty.buffer.ByteBuf;

public abstract class RpcDecoder implements MessageBuffer.DataConsumer {

  protected final MessageBuffer buffer = new MessageBuffer();

  public void onDataReceived(final ByteBuf byteBuf) throws RpcException {
    buffer.appendData(byteBuf);
    buffer.consumeData(this);
  }

  public void close() throws RpcException {
    final boolean consumedAllData = buffer.isEmpty();
    buffer.close();
    if (!consumedAllData) {
      throw RpcException.INCORRECT_LENGTH_ERROR;
    }
  }
}
