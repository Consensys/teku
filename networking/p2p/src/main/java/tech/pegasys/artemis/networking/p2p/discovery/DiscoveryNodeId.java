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

package tech.pegasys.artemis.networking.p2p.discovery;

import io.libp2p.etc.encode.Base58;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.artemis.networking.p2p.peer.NodeId;

public class DiscoveryNodeId extends NodeId {
  private final Bytes nodeId;

  public DiscoveryNodeId(final Bytes nodeId) {
    this.nodeId = nodeId;
  }

  @Override
  public Bytes toBytes() {
    return nodeId;
  }

  @Override
  public String toBase58() {
    return Base58.INSTANCE.encode(nodeId.toArrayUnsafe());
  }
}
