/*
 * Copyright ConsenSys Software Inc., 2022
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

import java.util.Optional;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.BeaconChainMethods;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.MetadataMessagesFactory;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.StatusMessageFactory;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

public class Eth2PeerFactory {

  private static final long TIME_OUT = 60;
  private final Spec spec;
  private final StatusMessageFactory statusMessageFactory;
  private final MetadataMessagesFactory metadataMessagesFactory;
  private final MetricsSystem metricsSystem;
  private final CombinedChainDataClient chainDataClient;
  private final TimeProvider timeProvider;
  private final Optional<Checkpoint> requiredCheckpoint;
  private final int peerRateLimit;
  private final int peerRequestLimit;

  public Eth2PeerFactory(
      final Spec spec,
      final MetricsSystem metricsSystem,
      final CombinedChainDataClient chainDataClient,
      final StatusMessageFactory statusMessageFactory,
      final MetadataMessagesFactory metadataMessagesFactory,
      final TimeProvider timeProvider,
      final Optional<Checkpoint> requiredCheckpoint,
      final int peerRateLimit,
      final int peerRequestLimit) {
    this.spec = spec;
    this.metricsSystem = metricsSystem;
    this.chainDataClient = chainDataClient;
    this.timeProvider = timeProvider;
    this.statusMessageFactory = statusMessageFactory;
    this.metadataMessagesFactory = metadataMessagesFactory;
    this.requiredCheckpoint = requiredCheckpoint;
    this.peerRateLimit = peerRateLimit;
    this.peerRequestLimit = peerRequestLimit;
  }

  public Eth2Peer create(final Peer peer, final BeaconChainMethods rpcMethods) {
    return Eth2Peer.create(
        spec,
        peer,
        rpcMethods,
        statusMessageFactory,
        metadataMessagesFactory,
        PeerChainValidator.create(spec, metricsSystem, chainDataClient, requiredCheckpoint),
        RateTracker.create(peerRateLimit, TIME_OUT, timeProvider),
        RateTracker.create(
            peerRateLimit * spec.getMaxBlobsPerBlock().orElse(1), TIME_OUT, timeProvider),
        RateTracker.create(peerRequestLimit, TIME_OUT, timeProvider));
  }
}
