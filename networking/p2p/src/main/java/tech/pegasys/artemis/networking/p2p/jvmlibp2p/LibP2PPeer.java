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
import tech.pegasys.artemis.networking.p2p.peer.NodeId;
import tech.pegasys.artemis.networking.p2p.peer.Peer;

public class LibP2PPeer implements Peer {
  private final Connection connection;
  private final NodeId nodeId;

  public LibP2PPeer(final Connection connection) {
    this.connection = connection;
    final PeerId peerId = connection.getSecureSession().getRemoteId();
    nodeId = new LibP2PNodeId(peerId);
  }

  @Override
  public NodeId getId() {
    return nodeId;
  }

  @Override
  public boolean isConnected() {
    return connection.getNettyChannel().isOpen();
  }

  @Override
  public Connection getConnection() {
    return connection;
  }

  @Override
  public boolean connectionInitiatedLocally() {
    return connection.isInitiator();
  }

  @Override
  public boolean connectionInitiatedRemotely() {
    return !connectionInitiatedLocally();
  }
}
