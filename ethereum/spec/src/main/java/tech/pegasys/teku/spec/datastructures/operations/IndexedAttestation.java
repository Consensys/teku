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
import tech.pegasys.teku.infrastructure.ssz.containers.Container3;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema3;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszUInt64ListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.datastructures.type.SszSignatureSchema;

public class IndexedAttestation
    extends Container3<IndexedAttestation, SszUInt64List, AttestationData, SszSignature> {

  public static class IndexedAttestationSchema
      extends ContainerSchema3<IndexedAttestation, SszUInt64List, AttestationData, SszSignature> {

    public IndexedAttestationSchema(final SpecConfig config) {
      super(
          "IndexedAttestation",
          namedSchema(
              "attesting_indices",
              SszUInt64ListSchema.create(config.getMaxValidatorsPerCommittee())),
          namedSchema("data", AttestationData.SSZ_SCHEMA),
          namedSchema("signature", SszSignatureSchema.INSTANCE));
    }

    public SszUInt64ListSchema<?> getAttestingIndicesSchema() {
      return (SszUInt64ListSchema<?>) super.getFieldSchema0();
    }

    @Override
    public IndexedAttestation createFromBackingNode(final TreeNode node) {
      return new IndexedAttestation(this, node);
    }

    public IndexedAttestation create(
        final SszUInt64List attestingIndices,
        final AttestationData data,
        final BLSSignature signature) {
      return new IndexedAttestation(this, attestingIndices, data, signature);
    }
  }

  private IndexedAttestation(final IndexedAttestationSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  private IndexedAttestation(
      final IndexedAttestationSchema schema,
      final SszUInt64List attestingIndices,
      final AttestationData data,
      final BLSSignature signature) {
    super(schema, attestingIndices, data, new SszSignature(signature));
  }

  @Override
  public IndexedAttestationSchema getSchema() {
    return (IndexedAttestationSchema) super.getSchema();
  }

  public SszUInt64List getAttestingIndices() {
    return getField0();
  }

  public AttestationData getData() {
    return getField1();
  }

  public BLSSignature getSignature() {
    return getField2().getSignature();
  }
}
