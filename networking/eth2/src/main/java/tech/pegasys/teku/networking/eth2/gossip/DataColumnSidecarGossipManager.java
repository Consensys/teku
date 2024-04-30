/*
 * Copyright Consensys Software Inc., 2022
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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.networking.eth2.gossip.subnets.DataColumnSidecarSubnetSubscriptions;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;

public class DataColumnSidecarGossipManager implements GossipManager {
  private static final Logger LOG = LogManager.getLogger();

  private final DataColumnSidecarSubnetSubscriptions subnetSubscriptions;

  public DataColumnSidecarGossipManager(
      final DataColumnSidecarSubnetSubscriptions dataColumnSidecarSubnetSubscriptions) {
    subnetSubscriptions = dataColumnSidecarSubnetSubscriptions;
  }

  public void publish(final DataColumnSidecar dataColumnSidecar) {
    subnetSubscriptions
        .gossip(dataColumnSidecar)
        .finish(
            __ -> {
              LOG.debug(
                  "Successfully published data column sidecar for slot {}", dataColumnSidecar);
            },
            error -> {
              LOG.warn("Error publishing data column sidecar for slot {}", dataColumnSidecar);
            });
  }

  public void subscribeToSubnetId(final int subnetId) {
    LOG.trace("Subscribing to subnet ID {}", subnetId);
    subnetSubscriptions.subscribeToSubnetId(subnetId);
  }

  public void unsubscribeFromSubnetId(final int subnetId) {
    LOG.trace("Unsubscribing to subnet ID {}", subnetId);
    subnetSubscriptions.unsubscribeFromSubnetId(subnetId);
  }

  @Override
  public void subscribe() {
    subnetSubscriptions.subscribe();
  }

  @Override
  public void unsubscribe() {
    subnetSubscriptions.unsubscribe();
  }
}
