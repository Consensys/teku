/*
 * Copyright Consensys Software Inc., 2022
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
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema3;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszUInt64ListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.datastructures.type.SszSignatureSchema;

public class IndexedPayloadAttestationSchema
    extends ContainerSchema3<
        IndexedPayloadAttestation, SszUInt64List, PayloadAttestationData, SszSignature> {

  public IndexedPayloadAttestationSchema(final long ptcSize) {
    super(
        "IndexedPayloadAttestation",
        namedSchema("attesting_indices", SszUInt64ListSchema.create(ptcSize)),
        namedSchema("data", PayloadAttestationData.SSZ_SCHEMA),
        namedSchema("signature", SszSignatureSchema.INSTANCE));
  }

  public IndexedPayloadAttestation create(
      final SszUInt64List attestingIndices,
      final PayloadAttestationData data,
      final BLSSignature signature) {
    return new IndexedPayloadAttestation(this, attestingIndices, data, signature);
  }

  @SuppressWarnings("unchecked")
  public SszUInt64ListSchema<SszUInt64List> getAttestingIndicesSchema() {
    return (SszUInt64ListSchema<SszUInt64List>) getChildSchema(getFieldIndex("attesting_indices"));
  }

  @Override
  public IndexedPayloadAttestation createFromBackingNode(final TreeNode node) {
    return new IndexedPayloadAttestation(this, node);
  }
}
