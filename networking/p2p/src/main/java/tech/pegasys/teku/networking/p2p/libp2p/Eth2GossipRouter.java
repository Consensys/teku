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

package tech.pegasys.teku.networking.p2p.libp2p;

import io.libp2p.pubsub.gossip.GossipRouter;
import java.util.Base64;
import org.apache.tuweni.crypto.Hash;
import org.jetbrains.annotations.NotNull;
import pubsub.pb.Rpc.Message;

/** Customization of a standard Libp2p Gossip router for Eth2 spec */
public class Eth2GossipRouter extends GossipRouter {

  @NotNull
  @Override
  protected String getMessageId(@NotNull Message msg) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(Hash.sha2_256(msg.getData().toByteArray()));
  }
}
