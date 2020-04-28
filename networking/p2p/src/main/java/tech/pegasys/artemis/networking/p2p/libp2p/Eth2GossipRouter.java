package tech.pegasys.artemis.networking.p2p.libp2p;

import io.libp2p.pubsub.gossip.GossipRouter;
import org.apache.tuweni.crypto.Hash;
import org.apache.tuweni.io.Base64;
import org.jetbrains.annotations.NotNull;
import pubsub.pb.Rpc.Message;

/** Customization of a standard Libp2p Gossip router for Eth2 spec */
public class Eth2GossipRouter extends GossipRouter {

  @NotNull
  @Override
  protected String getMessageId(@NotNull Message msg) {
    return Base64.encodeBytes(Hash.sha2_256(msg.getData().toByteArray()));
  }
}
