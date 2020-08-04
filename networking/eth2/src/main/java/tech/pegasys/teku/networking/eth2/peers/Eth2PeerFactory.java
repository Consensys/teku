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

package tech.pegasys.teku.networking.eth2.peers;

import tech.pegasys.teku.networking.eth2.rpc.beaconchain.BeaconChainMethods;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.MetadataMessagesFactory;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.StatusMessageFactory;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.util.time.TimeProvider;

public class Eth2PeerFactory {

  private final StatusMessageFactory statusMessageFactory;
  private final MetadataMessagesFactory metadataMessagesFactory;
  private final TimeProvider timeProvider;
  private final int peerRateLimit;
  private final int peerRequestLimit;

  public Eth2PeerFactory(
      final StatusMessageFactory statusMessageFactory,
      final MetadataMessagesFactory metadataMessagesFactory,
      final TimeProvider timeProvider,
      final int peerRateLimit,
      final int peerRequestLimit) {
    this.timeProvider = timeProvider;
    this.statusMessageFactory = statusMessageFactory;
    this.metadataMessagesFactory = metadataMessagesFactory;
    this.peerRateLimit = peerRateLimit;
    this.peerRequestLimit = peerRequestLimit;
  }

  public Eth2Peer create(final Peer peer, final BeaconChainMethods rpcMethods) {
    return new Eth2Peer(
        peer,
        rpcMethods,
        statusMessageFactory,
        metadataMessagesFactory,
        timeProvider,
        peerRateLimit,
        peerRequestLimit);
  }
}
