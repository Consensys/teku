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

package tech.pegasys.teku.networking.p2p.libp2p.gossip;

import io.libp2p.etc.types.WBytes;
import io.libp2p.pubsub.PubsubMessage;
import org.jetbrains.annotations.NotNull;
import pubsub.pb.Rpc.Message;
import tech.pegasys.teku.networking.p2p.gossip.PreparedMessage;

public class PreparedPubsubMessage implements PubsubMessage {

  private final Message protobufMessage;
  private final PreparedMessage preparedMessage;

  public PreparedPubsubMessage(Message protobufMessage, PreparedMessage preparedMessage) {
    this.protobufMessage = protobufMessage;
    this.preparedMessage = preparedMessage;
  }

  @NotNull
  @Override
  public WBytes getMessageId() {
    return new WBytes(preparedMessage.getMessageId().toArrayUnsafe());
  }

  @NotNull
  @Override
  public Message getProtobufMessage() {
    return protobufMessage;
  }

  public PreparedMessage getPreparedMessage() {
    return preparedMessage;
  }
}
