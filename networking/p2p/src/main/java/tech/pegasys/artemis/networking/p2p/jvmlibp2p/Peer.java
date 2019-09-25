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

package tech.pegasys.artemis.networking.p2p.jvmlibp2p;

import io.libp2p.core.Connection;
import io.libp2p.core.PeerId;
import io.libp2p.core.multiformats.Multiaddr;
import java.util.concurrent.CompletableFuture;
import tech.pegasys.artemis.datastructures.networking.libp2p.rpc.HelloMessage;

public class Peer {

  final Connection connection;
  final CompletableFuture<HelloMessage> remoteHello = new CompletableFuture<>();

  public Peer(Connection connection) {
    this.connection = connection;
  }

  public CompletableFuture<HelloMessage> getRemoteHelloMessage() {
    return remoteHello;
  }

  public Connection getConnection() {
    return connection;
  }

  public PeerId getPeerId() {
    return connection.getSecureSession().getRemoteId();
  }

  public Multiaddr getPeerMultiaddr() {
    return connection.remoteAddress();
  }
}
