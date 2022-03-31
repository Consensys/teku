/*
 * Copyright 2022 ConsenSys AG.
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

package tech.pegasys.teku.beaconrestapi.handlers.v1.events;

import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BYTES32_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BYTES_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.ContributionAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SignedContributionAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeContribution;

public class ContributionAndProofEvent extends Event<SignedContributionAndProof> {

  private static final SerializableTypeDefinition<SyncCommitteeContribution> CONTRIBUTION_TYPE =
      SerializableTypeDefinition.object(SyncCommitteeContribution.class)
          .withField("slot", UINT64_TYPE, SyncCommitteeContribution::getSlot)
          .withField(
              "beacon_block_root", BYTES32_TYPE, SyncCommitteeContribution::getBeaconBlockRoot)
          .withField(
              "subcommittee_index", UINT64_TYPE, SyncCommitteeContribution::getSubcommitteeIndex)
          .withField(
              "aggregation_bits",
              BYTES_TYPE,
              contribution -> contribution.getAggregationBits().sszSerialize())
          .withField(
              "signature",
              BYTES_TYPE,
              contribution -> contribution.getSignature().toBytesCompressed())
          .build();

  private static final SerializableTypeDefinition<ContributionAndProof>
      CONTRIBUTION_AND_PROOF_TYPE =
          SerializableTypeDefinition.object(ContributionAndProof.class)
              .withField("aggregator_index", UINT64_TYPE, ContributionAndProof::getAggregatorIndex)
              .withField(
                  "selection_proof",
                  BYTES_TYPE,
                  contributionAndProof ->
                      contributionAndProof.getSelectionProof().toBytesCompressed())
              .withField("contribution", CONTRIBUTION_TYPE, ContributionAndProof::getContribution)
              .build();

  private static final SerializableTypeDefinition<SignedContributionAndProof> EVENT_TYPE =
      SerializableTypeDefinition.object(SignedContributionAndProof.class)
          .withField("message", CONTRIBUTION_AND_PROOF_TYPE, SignedContributionAndProof::getMessage)
          .withField("signature", BYTES_TYPE, event -> event.getSignature().toBytesCompressed())
          .build();

  ContributionAndProofEvent(SignedContributionAndProof signedContributionAndProof) {
    super(EVENT_TYPE, signedContributionAndProof);
  }
}
