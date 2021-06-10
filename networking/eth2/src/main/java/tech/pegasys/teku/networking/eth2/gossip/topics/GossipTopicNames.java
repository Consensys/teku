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

public class GossipTopicNames {

  public static String BEACON_BLOCK = "beacon_block";
  public static String BEACON_AGGREGATE_AND_PROOF = "beacon_aggregate_and_proof";
  public static String ATTESTER_SLASHING = "attester_slashing";
  public static String PROPOSER_SLASHING = "proposer_slashing";
  public static String VOLUNTARY_EXIT = "voluntary_exit";
  public static String SYNC_COMMITTEE_CONTRIBUTION_AND_PROOF =
      "sync_committee_contribution_and_proof";

  public static String getAttestationSubnetTopicName(final int subnetId) {
    return "beacon_attestation_" + subnetId;
  }

  public static String getSyncCommitteeSubnetTopicName(final int subnetId) {
    return "sync_committee_" + subnetId;
  }
}
