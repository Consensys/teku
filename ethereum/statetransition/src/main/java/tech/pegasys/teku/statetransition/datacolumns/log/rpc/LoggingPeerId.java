/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.statetransition.datacolumns.log.rpc;

import java.util.Optional;
import org.apache.tuweni.units.bigints.UInt256;

public class LoggingPeerId {
  public static LoggingPeerId fromNodeId(final UInt256 nodeId) {
    return new LoggingPeerId(nodeId, Optional.empty());
  }

  public static LoggingPeerId fromPeerAndNodeId(final String base58PeerId, final UInt256 nodeId) {
    return new LoggingPeerId(nodeId, Optional.of(base58PeerId));
  }

  private final UInt256 nodeId;
  private final Optional<String> base58PeerId;

  public LoggingPeerId(final UInt256 nodeId, final Optional<String> base58PeerId) {
    this.nodeId = nodeId;
    this.base58PeerId = base58PeerId;
  }

  @Override
  public String toString() {
    final String sNodeId = nodeId.toHexString();
    final String sShortNodeId =
        sNodeId.substring(0, 10) + "..." + sNodeId.substring(sNodeId.length() - 8);
    return base58PeerId.map(s -> s + " (nodeId = " + sShortNodeId + ")").orElse(sShortNodeId);
  }
}
