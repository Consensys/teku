/*
 * Copyright 2019 ConsenSys AG.
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
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.datastructures.type.SszSignatureSchema;
import tech.pegasys.teku.util.config.Constants;

public class IndexedAttestation
    extends Container3<IndexedAttestation, SszUInt64List, AttestationData, SszSignature> {

  public static class IndexedAttestationSchema
      extends ContainerSchema3<IndexedAttestation, SszUInt64List, AttestationData, SszSignature> {

    public IndexedAttestationSchema() {
      super(
          "IndexedAttestation",
          namedSchema(
              "attesting_indices",
              SszUInt64ListSchema.create(Constants.MAX_VALIDATORS_PER_COMMITTEE)),
          namedSchema("data", AttestationData.SSZ_SCHEMA),
          namedSchema("signature", SszSignatureSchema.INSTANCE));
    }

    public SszUInt64ListSchema<?> getAttestingIndicesSchema() {
      return (SszUInt64ListSchema<?>) super.getFieldSchema0();
    }

    @Override
    public IndexedAttestation createFromBackingNode(TreeNode node) {
      return new IndexedAttestation(this, node);
    }
  }

  public static final IndexedAttestationSchema SSZ_SCHEMA = new IndexedAttestationSchema();

  private IndexedAttestation(IndexedAttestationSchema type, TreeNode backingNode) {
    super(type, backingNode);
  }

  public IndexedAttestation(
      SszUInt64List attesting_indices, AttestationData data, BLSSignature signature) {
    super(SSZ_SCHEMA, attesting_indices, data, new SszSignature(signature));
  }

  public SszUInt64List getAttesting_indices() {
    return getField0();
  }

  public AttestationData getData() {
    return getField1();
  }

  public BLSSignature getSignature() {
    return getField2().getSignature();
  }
}
