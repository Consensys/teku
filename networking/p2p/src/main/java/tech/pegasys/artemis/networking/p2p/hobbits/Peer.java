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

package tech.pegasys.artemis.networking.p2p.hobbits;

import java.net.URI;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.artemis.networking.p2p.hobbits.rpc.GetStatusMessage;
import tech.pegasys.artemis.networking.p2p.hobbits.rpc.HelloMessage;

/** A hobbits peer */
public final class Peer implements org.apache.tuweni.plumtree.Peer {

  private final URI uri;
  private HelloMessage peerHello;
  private GetStatusMessage peerStatus;
  private Bytes peerGossip;
  private boolean active = true;

  public Peer(URI peer) {
    this.uri = peer;
  }

  public void setPeerHello(HelloMessage peerHello) {
    this.peerHello = peerHello;
  }

  public void setPeerStatus(GetStatusMessage peerStatus) {
    this.peerStatus = peerStatus;
  }

  public void setPeerGossip(Bytes data) {
    if (Objects.nonNull(data)) {
      this.peerGossip = data;
    }
  }

  public void setInactive() {
    active = false;
  }

  public GetStatusMessage peerStatus() {
    return peerStatus;
  }

  public HelloMessage peerHello() {
    return peerHello;
  }

  public Bytes peerGossip() {
    return this.peerGossip;
  }

  public URI uri() {
    return this.uri;
  }

  public boolean active() {
    return active;
  }
}
