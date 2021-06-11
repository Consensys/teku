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

package tech.pegasys.teku.networking.eth2.gossip.topics;

import java.util.Locale;

public enum GossipTopicName {
  BEACON_BLOCK,
  BEACON_AGGREGATE_AND_PROOF,
  ATTESTER_SLASHING,
  PROPOSER_SLASHING,
  VOLUNTARY_EXIT,
  SYNC_COMMITTEE_CONTRIBUTION_AND_PROOF;

  @Override
  public String toString() {
    return name().toLowerCase(Locale.US);
  }

  public static String getAttestationSubnetTopicName(final int subnetId) {
    return "beacon_attestation_" + subnetId;
  }

  public static String getSyncCommitteeSubnetTopicName(final int subnetId) {
    return "sync_committee_" + subnetId;
  }
}
