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

package tech.pegasys.teku.api.schema.electra;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;
import tech.pegasys.teku.api.schema.interfaces.AttesterSlashingContainer;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing.AttesterSlashingSchema;

@SuppressWarnings("JavaCase")
public class AttesterSlashingElectra implements AttesterSlashingContainer {
  public final IndexedAttestationElectra attestation_1;
  public final IndexedAttestationElectra attestation_2;

  public AttesterSlashingElectra(AttesterSlashing attesterSlashing) {
    this.attestation_1 = new IndexedAttestationElectra(attesterSlashing.getAttestation1());
    this.attestation_2 = new IndexedAttestationElectra(attesterSlashing.getAttestation2());
  }

  @JsonCreator
  public AttesterSlashingElectra(
      @JsonProperty("attestation_1") final IndexedAttestationElectra attestation_1,
      @JsonProperty("attestation_2") final IndexedAttestationElectra attestation_2) {
    this.attestation_1 = attestation_1;
    this.attestation_2 = attestation_2;
  }

  public AttesterSlashing asInternalAttesterSlashing(final Spec spec) {
    return asInternalAttesterSlashing(spec.atSlot(attestation_1.data.slot));
  }

  @Override
  public AttesterSlashing asInternalAttesterSlashing(final SpecVersion spec) {
    final AttesterSlashingSchema attesterSlashingSchema =
        spec.getSchemaDefinitions().getAttesterSlashingSchema();
    return attesterSlashingSchema.create(
        attestation_1.asInternalIndexedAttestation(spec),
        attestation_2.asInternalIndexedAttestation(spec));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof AttesterSlashingElectra)) {
      return false;
    }
    AttesterSlashingElectra that = (AttesterSlashingElectra) o;
    return Objects.equals(attestation_1, that.attestation_1)
        && Objects.equals(attestation_2, that.attestation_2);
  }

  @Override
  public int hashCode() {
    return Objects.hash(attestation_1, attestation_2);
  }
}
