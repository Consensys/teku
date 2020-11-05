package tech.pegasys.teku.networking.p2p.libp2p.gossip;

import io.libp2p.etc.types.WBytes;
import io.libp2p.pubsub.PubsubMessage;
import org.jetbrains.annotations.NotNull;
import pubsub.pb.Rpc.Message;
import tech.pegasys.teku.networking.p2p.gossip.PreparedMessage;

public class PreparedPubsubMessage implements PubsubMessage {

  private final Message protobufMessage;
  private final PreparedMessage preparedMessage;

  public PreparedPubsubMessage(Message protobufMessage,
      PreparedMessage preparedMessage) {
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
