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

package tech.pegasys.artemis.networking.eth2.gossip;

import static com.google.common.primitives.UnsignedLong.ZERO;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.max;
import static tech.pegasys.artemis.util.config.Constants.ATTESTATION_SUBNET_COUNT;

import com.google.common.primitives.UnsignedLong;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import tech.pegasys.artemis.networking.eth2.Eth2Network;
import tech.pegasys.artemis.ssz.SSZTypes.Bitvector;
import tech.pegasys.artemis.util.time.channels.SlotEventsChannel;

public class AttestationTopicSubscriptions implements SlotEventsChannel {
  private final Map<Integer, UnsignedLong> unsubscriptionSlotByCommittee = new HashMap<>();
  private final Map<Integer, Integer> subscribedCommitteeCountBySubnetIndex =
      IntStream.range(0, ATTESTATION_SUBNET_COUNT)
          .boxed()
          .collect(Collectors.toMap(i -> i, i -> 0));
  private final Eth2Network eth2Network;
  private final Bitvector attestationSubnetBitfield = new Bitvector(ATTESTATION_SUBNET_COUNT);

  public AttestationTopicSubscriptions(final Eth2Network eth2Network) {
    this.eth2Network = eth2Network;
  }

  public synchronized void subscribeToCommittee(
      final int committeeIndex, final UnsignedLong aggregationSlot) {
    eth2Network.subscribeToAttestationCommitteeTopic(committeeIndex);
    final UnsignedLong currentUnsubscribeSlot =
        unsubscriptionSlotByCommittee.getOrDefault(committeeIndex, ZERO);
    unsubscriptionSlotByCommittee.put(committeeIndex, max(currentUnsubscribeSlot, aggregationSlot));
    handleCommitteeSubscriptionENRChange(committeeIndex);
  }

  @Override
  public synchronized void onSlot(final UnsignedLong slot) {
    final Iterator<Entry<Integer, UnsignedLong>> iterator =
        unsubscriptionSlotByCommittee.entrySet().iterator();
    while (iterator.hasNext()) {
      final Entry<Integer, UnsignedLong> entry = iterator.next();
      if (entry.getValue().compareTo(slot) < 0) {
        iterator.remove();
        int committeeIndex = entry.getKey();
        eth2Network.unsubscribeFromAttestationCommitteeTopic(committeeIndex);
        handleCommitteeUnsubscriptionENRChange(committeeIndex);
      }
    }
  }

  private void handleCommitteeSubscriptionENRChange(int committeeIndex) {
    int subnetIndex = committeeIndex % ATTESTATION_SUBNET_COUNT;
    int committeeCount = subscribedCommitteeCountBySubnetIndex.get(subnetIndex) + 1;
    subscribedCommitteeCountBySubnetIndex.put(subnetIndex, committeeCount);
    if (committeeCount == 1) {
      attestationSubnetBitfield.setBit(subnetIndex);
      eth2Network.updateAttestationSubnetENRField(attestationSubnetBitfield.serialize());
    }
  }

  private void handleCommitteeUnsubscriptionENRChange(int committeeIndex) {
    int subnetIndex = committeeIndex % ATTESTATION_SUBNET_COUNT;
    int committeeCount = subscribedCommitteeCountBySubnetIndex.get(subnetIndex) - 1;
    subscribedCommitteeCountBySubnetIndex.put(subnetIndex, committeeCount);
    if (committeeCount == 0) {
      attestationSubnetBitfield.clearBit(subnetIndex);
      eth2Network.updateAttestationSubnetENRField(attestationSubnetBitfield.serialize());
    }
  }
}
