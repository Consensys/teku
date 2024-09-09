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

package tech.pegasys.teku.spec.datastructures.operations;

import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.collections.SszUInt64List;
import tech.pegasys.teku.infrastructure.ssz.schema.SszContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszUInt64ListSchema;

public interface IndexedAttestationSchema<T extends IndexedAttestation>
    extends SszContainerSchema<T> {

  @SuppressWarnings("unchecked")
  default IndexedAttestationSchema<IndexedAttestation> castTypeToIndexedAttestationSchema() {
    return (IndexedAttestationSchema<IndexedAttestation>) this;
  }

  SszUInt64ListSchema<?> getAttestingIndicesSchema();

  IndexedAttestation create(
      SszUInt64List attestingIndices, AttestationData data, BLSSignature signature);
}
