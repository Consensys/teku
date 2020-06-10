package tech.pegasys.teku.networking.eth2.rpc.core.encodings;

import io.netty.buffer.ByteBuf;
import java.util.Optional;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;

public interface RpcByteBufDecoder<TMessage> {

  Optional<TMessage> decodeOneMessage(ByteBuf in) throws RpcException;

  void complete() throws RpcException;
}
