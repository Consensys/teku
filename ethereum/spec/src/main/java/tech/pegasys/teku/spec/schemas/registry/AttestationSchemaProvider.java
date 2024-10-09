/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.spec.schemas.registry;

import static tech.pegasys.teku.spec.SpecMilestone.DENEB;
import static tech.pegasys.teku.spec.SpecMilestone.ELECTRA;
import static tech.pegasys.teku.spec.SpecMilestone.PHASE0;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.ATTESTATION_SCHEMA;

import java.util.Set;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationSchema;
import tech.pegasys.teku.spec.datastructures.operations.versions.electra.AttestationElectraSchema;
import tech.pegasys.teku.spec.datastructures.operations.versions.phase0.AttestationPhase0Schema;

public class AttestationSchemaProvider
    extends AbstractSchemaProvider<AttestationSchema<Attestation>> {

  public AttestationSchemaProvider() {
    super(ATTESTATION_SCHEMA);
    addMilestoneMapping(PHASE0, DENEB);
    addMilestoneMapping(ELECTRA, SpecMilestone.getHighestMilestone());
  }

  @Override
  protected AttestationSchema<Attestation> createSchema(
      final SchemaRegistry registry,
      final SpecMilestone effectiveMilestone,
      final SpecConfig specConfig) {
    return switch (effectiveMilestone) {
      case PHASE0 ->
          new AttestationPhase0Schema(specConfig.getMaxValidatorsPerCommittee())
              .castTypeToAttestationSchema();
      case ELECTRA ->
          new AttestationElectraSchema(
                  (long) specConfig.getMaxValidatorsPerCommittee()
                      * specConfig.getMaxCommitteesPerSlot(),
                  specConfig.getMaxCommitteesPerSlot())
              .castTypeToAttestationSchema();
      default ->
          throw new IllegalArgumentException(
              "It is not supposed to create a specific version for " + effectiveMilestone);
    };
  }

  @Override
  public Set<SpecMilestone> getSupportedMilestones() {
    return ALL_MILESTONES;
  }
}
