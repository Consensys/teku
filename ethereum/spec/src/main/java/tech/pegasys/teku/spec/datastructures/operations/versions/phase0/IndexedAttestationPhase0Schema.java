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

package tech.pegasys.teku.spec.datastructures.operations.versions.phase0;

import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.collections.SszUInt64List;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema3;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszUInt64ListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestationSchema;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.datastructures.type.SszSignatureSchema;

public class IndexedAttestationPhase0Schema
    extends ContainerSchema3<IndexedAttestationPhase0, SszUInt64List, AttestationData, SszSignature>
    implements IndexedAttestationSchema<IndexedAttestationPhase0> {

  public IndexedAttestationPhase0Schema(final long maxValidatorsPerIndexedAttestation) {
    super(
        "IndexedAttestationPhase0",
        namedSchema(
            "attesting_indices", SszUInt64ListSchema.create(maxValidatorsPerIndexedAttestation)),
        namedSchema("data", AttestationData.SSZ_SCHEMA),
        namedSchema("signature", SszSignatureSchema.INSTANCE));
  }

  @Override
  public SszUInt64ListSchema<?> getAttestingIndicesSchema() {
    return (SszUInt64ListSchema<?>) super.getFieldSchema0();
  }

  @Override
  public IndexedAttestationPhase0 createFromBackingNode(final TreeNode node) {
    return new IndexedAttestationPhase0(this, node);
  }

  @Override
  public IndexedAttestation create(
      final SszUInt64List attestingIndices,
      final AttestationData data,
      final BLSSignature signature) {
    return new IndexedAttestationPhase0(this, attestingIndices, data, signature);
  }
}
