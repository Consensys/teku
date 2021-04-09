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

package tech.pegasys.teku.validator.client.signer;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum SignType {
  @JsonProperty("randao_reveal")
  RANDAO_REVEAL,
  @JsonProperty("block")
  BLOCK,
  @JsonProperty("attestation")
  ATTESTATION,
  @JsonProperty("aggregation_slot")
  AGGREGATION_SLOT,
  @JsonProperty("aggregate_and_proof")
  AGGREGATE_AND_PROOF,
  @JsonProperty("voluntary_exit")
  VOLUNTARY_EXIT,
  @JsonProperty("sync_committee_signature")
  SYNC_COMMITTEE_SIGNATURE,
  @JsonProperty("sync_committee_selection_proof")
  SYNC_COMMITTEE_SELECTION_PROOF,
  @JsonProperty("sync_committee_contribution_and_proof")
  SYNC_COMMITTEE_CONTRIBUTION_AND_PROOF
}
