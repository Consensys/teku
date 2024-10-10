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
import static tech.pegasys.teku.spec.SpecMilestone.PHASE0;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.ATTESTATION_SCHEMA;

import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationSchema;
import tech.pegasys.teku.spec.datastructures.operations.versions.electra.AttestationElectraSchema;
import tech.pegasys.teku.spec.datastructures.operations.versions.phase0.AttestationPhase0Schema;

public class AttestationSchemaProvider
    extends AbstractSchemaProvider<AttestationSchema<Attestation>> {
  public AttestationSchemaProvider() {
    super(
        ATTESTATION_SCHEMA,
        schemaCreator(
            PHASE0,
            (registry, specConfig) ->
                new AttestationPhase0Schema(specConfig.getMaxValidatorsPerCommittee())
                    .castTypeToAttestationSchema()),
        schemaCreator(
            DENEB,
            (registry, specConfig) ->
                new AttestationElectraSchema(
                        (long) specConfig.getMaxValidatorsPerCommittee()
                            * specConfig.getMaxCommitteesPerSlot(),
                        specConfig.getMaxCommitteesPerSlot())
                    .castTypeToAttestationSchema()));
  }
}
