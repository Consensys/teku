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

package tech.pegasys.teku.networking.eth2.gossip.subnets;

import static java.util.stream.Collectors.toSet;
import static tech.pegasys.teku.util.config.Constants.ATTESTATION_SUBNET_COUNT;

import java.util.Set;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.datastructures.validator.SubnetSubscription;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class AllSubnetsSubscriber implements StableSubnetSubscriber {
  private static final Logger LOG = LogManager.getLogger();

  public static StableSubnetSubscriber create(final AttestationTopicSubscriber subscriber) {
    LOG.info("Subscribing to all attestation subnets");
    final Set<SubnetSubscription> subscriptions =
        IntStream.range(0, ATTESTATION_SUBNET_COUNT)
            .mapToObj(subnetId -> new SubnetSubscription(subnetId, UInt64.MAX_VALUE))
            .collect(toSet());
    subscriber.subscribeToPersistentSubnets(subscriptions);
    return new AllSubnetsSubscriber();
  }

  @Override
  public void onSlot(final UInt64 slot, final int validatorCount) {
    // Already subscribed to all subnets
  }
}
