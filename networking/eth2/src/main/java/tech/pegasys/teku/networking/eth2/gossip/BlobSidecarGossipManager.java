/*
 * Copyright ConsenSys Software Inc., 2023
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

package tech.pegasys.teku.networking.eth2.gossip;

import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.GossipTopicName;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.storage.client.RecentChainData;

public class BlobSidecarGossipManager extends AbstractGossipManager<BlobSidecar> {

  public BlobSidecarGossipManager(
      final RecentChainData recentChainData,
      final Spec spec,
      final AsyncRunner asyncRunner,
      final GossipNetwork gossipNetwork,
      final GossipEncoding gossipEncoding,
      final ForkInfo forkInfo,
      final OperationProcessor<BlobSidecar> processor,
      final int maxMessageSize) {
    super(
        recentChainData,
        GossipTopicName.BLOB_SIDECAR,
        asyncRunner,
        gossipNetwork,
        gossipEncoding,
        forkInfo,
        processor,
        spec.atEpoch(forkInfo.getFork().getEpoch())
            .getSchemaDefinitions()
            .toVersionDeneb()
            .orElseThrow()
            .getBlobSidecarSchema(),
        message -> spec.computeEpochAtSlot(message.getBlockSlot()),
        maxMessageSize);
  }

  public void publishBlobSidecar(final BlobSidecar message) {
    publishMessage(message);
  }

  @Override
  public boolean isEnabledDuringOptimisticSync() {
    return true;
  }
}
