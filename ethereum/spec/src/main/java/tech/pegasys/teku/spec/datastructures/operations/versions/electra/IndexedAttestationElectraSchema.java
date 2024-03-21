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

package tech.pegasys.teku.spec.datastructures.operations.versions.electra;

import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.collections.SszUInt64List;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema3;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszUInt64ListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestationContainerSchema;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.datastructures.type.SszSignatureSchema;

public class IndexedAttestationElectraSchema
    extends ContainerSchema3<
        IndexedAttestationElectra, SszUInt64List, AttestationData, SszSignature>
    implements IndexedAttestationContainerSchema<IndexedAttestationElectra> {

  public IndexedAttestationElectraSchema(final SpecConfig config) {
    super(
        "IndexedAttestation",
        namedSchema(
            "attesting_indices",
            SszUInt64ListSchema.create(
                (long) config.getMaxValidatorsPerCommittee() * config.getMaxCommitteesPerSlot())),
        namedSchema("data", AttestationData.SSZ_SCHEMA),
        namedSchema("signature", SszSignatureSchema.INSTANCE));
  }

  public SszUInt64ListSchema<?> getAttestingIndicesSchema() {
    return (SszUInt64ListSchema<?>) super.getFieldSchema0();
  }

  @Override
  public IndexedAttestationElectra createFromBackingNode(TreeNode node) {
    return new IndexedAttestationElectra(this, node);
  }

  public IndexedAttestationElectra create(
      final SszUInt64List attestingIndices,
      final AttestationData data,
      final BLSSignature signature) {
    return new IndexedAttestationElectra(this, attestingIndices, data, signature);
  }
}
