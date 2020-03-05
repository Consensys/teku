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

package tech.pegasys.artemis.networking.p2p.connection;

public class TargetPeerRange {
  private final int addPeersWhenLessThan;
  private final int dropPeersWhenGreaterThan;
  private final int midpoint;

  public TargetPeerRange(final int addPeersWhenLessThan, final int dropPeersWhenGreaterThan) {
    this.addPeersWhenLessThan = addPeersWhenLessThan;
    this.dropPeersWhenGreaterThan = dropPeersWhenGreaterThan;
    // Half way between the lower and upper bounds, rounded up
    this.midpoint = (addPeersWhenLessThan + dropPeersWhenGreaterThan + 1) / 2;
  }

  public int getPeersToAdd(final int currentPeerCount) {
    return currentPeerCount < addPeersWhenLessThan ? Math.max(0, midpoint - currentPeerCount) : 0;
  }

  public int getPeersToDrop(final int currentPeerCount) {
    return currentPeerCount > dropPeersWhenGreaterThan
        ? Math.max(0, currentPeerCount - midpoint)
        : 0;
  }
}
