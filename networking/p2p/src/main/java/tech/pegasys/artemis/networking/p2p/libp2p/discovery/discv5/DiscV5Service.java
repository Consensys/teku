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

package tech.pegasys.artemis.networking.p2p.libp2p.discovery.discv5;

import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.DiscoverySystem;
import org.ethereum.beacon.discovery.DiscoverySystemBuilder;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordBuilder;
import org.ethereum.beacon.discovery.schema.NodeRecordInfo;
import org.ethereum.beacon.discovery.schema.NodeStatus;
import tech.pegasys.artemis.networking.p2p.libp2p.discovery.DiscoveryPeer;
import tech.pegasys.artemis.networking.p2p.libp2p.discovery.DiscoveryService;
import tech.pegasys.artemis.util.async.SafeFuture;

public class DiscV5Service implements DiscoveryService {

  @SuppressWarnings("ComparatorMethodParameterNotUsed")
  public static final Comparator<NodeRecord> RANDOMLY =
      (o1, o2) -> ThreadLocalRandom.current().nextInt(-1, 2);

  private final DiscoverySystem discoverySystem;

  public DiscV5Service(final DiscoverySystem discoverySystem) {
    this.discoverySystem = discoverySystem;
  }

  public static DiscoveryService create(
      final Bytes privateKey, final String address, final int port, final String... bootnodes) {
    final DiscoverySystem discoveryManager =
        new DiscoverySystemBuilder()
            .privateKey(privateKey)
            .bootnodes(bootnodes)
            .localNodeRecord(
                new NodeRecordBuilder().privateKey(privateKey).address(address, port).build())
            .build();

    return new DiscV5Service(discoveryManager);
  }

  @Override
  public void start() {
    SafeFuture.of(discoverySystem.start()).join();
  }

  @Override
  public void stop() {
    discoverySystem.stop();
  }

  @Override
  public Stream<DiscoveryPeer> streamKnownPeers() {
    return activeNodes().map(NodeRecordConverter::convertToDiscoveryPeer).flatMap(Optional::stream);
  }

  @Override
  public CompletableFuture<Void> searchForPeers() {
    return randomActiveNode()
        .map(nodeRecord -> discoverySystem.findNodes(nodeRecord, 256))
        .orElseGet(
            () -> SafeFuture.failedFuture(new IllegalStateException("No active nodes to search")));
  }

  private Optional<NodeRecord> randomActiveNode() {
    return activeNodes().sorted(RANDOMLY).findAny();
  }

  private Stream<NodeRecord> activeNodes() {
    return discoverySystem
        .streamKnownNodes()
        .filter(record -> record.getStatus() == NodeStatus.ACTIVE)
        .map(NodeRecordInfo::getNode);
  }
}
