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

package tech.pegasys.teku.ethereum.executionlayer.client.schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing.AttesterSlashingSchema;

public class AttesterSlashingV1 {
  public final IndexedAttestationV1 attestation1;
  public final IndexedAttestationV1 attestation2;

  @JsonCreator
  public AttesterSlashingV1(
      @JsonProperty("attestation1") final IndexedAttestationV1 attestation1,
      @JsonProperty("attestation2") final IndexedAttestationV1 attestation2) {
    this.attestation1 = attestation1;
    this.attestation2 = attestation2;
  }

  public AttesterSlashingV1(
      tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing attesterSlashing) {
    attestation1 = new IndexedAttestationV1(attesterSlashing.getAttestation1());
    attestation2 = new IndexedAttestationV1(attesterSlashing.getAttestation2());
  }

  public tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing
      asInternalAttesterSlashing(final SpecVersion spec) {
    final AttesterSlashingSchema attesterSlashingSchema =
        spec.getSchemaDefinitions().getAttesterSlashingSchema();
    return attesterSlashingSchema.create(
        attestation1.asInternalIndexedAttestation(spec),
        attestation2.asInternalIndexedAttestation(spec));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof AttesterSlashingV1)) {
      return false;
    }
    AttesterSlashingV1 that = (AttesterSlashingV1) o;
    return Objects.equals(attestation1, that.attestation1)
        && Objects.equals(attestation2, that.attestation2);
  }

  @Override
  public int hashCode() {
    return Objects.hash(attestation1, attestation2);
  }
}
