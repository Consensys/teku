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

package tech.pegasys.artemis.networking.p2p.peer;

import io.libp2p.core.Connection;

public class DelegatingPeer implements Peer {
  private final Peer peer;

  public DelegatingPeer(final Peer peer) {
    this.peer = peer;
  }

  @Override
  public NodeId getId() {
    return peer.getId();
  }

  @Override
  public boolean isConnected() {
    return peer.isConnected();
  }

  @Override
  public Connection getConnection() {
    return peer.getConnection();
  }

  @Override
  public boolean connectionInitiatedLocally() {
    return peer.connectionInitiatedLocally();
  }

  @Override
  public boolean connectionInitiatedRemotely() {
    return peer.connectionInitiatedRemotely();
  }
}
