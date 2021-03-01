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
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.datastructures.type.SszSignatureSchema;
import tech.pegasys.teku.ssz.SSZTypes.SSZBackingList;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;
import tech.pegasys.teku.ssz.backing.SszList;
import tech.pegasys.teku.ssz.backing.containers.Container3;
import tech.pegasys.teku.ssz.backing.containers.ContainerSchema3;
import tech.pegasys.teku.ssz.backing.schema.SszListSchema;
import tech.pegasys.teku.ssz.backing.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.backing.schema.SszSchema;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.view.AbstractSszPrimitive;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszUInt64;
import tech.pegasys.teku.ssz.backing.view.SszUtils;
import tech.pegasys.teku.util.config.Constants;

public class IndexedAttestation
    extends Container3<IndexedAttestation, SszList<SszUInt64>, AttestationData, SszSignature> {

  public static class IndexedAttestationSchema
      extends ContainerSchema3<
          IndexedAttestation, SszList<SszUInt64>, AttestationData, SszSignature> {

    public IndexedAttestationSchema() {
      super(
          "IndexedAttestation",
          namedSchema(
              "attesting_indices",
              SszListSchema.create(
                  SszPrimitiveSchemas.UINT64_SCHEMA, Constants.MAX_VALIDATORS_PER_COMMITTEE)),
          namedSchema("data", AttestationData.SSZ_SCHEMA),
          namedSchema("signature", SszSignatureSchema.INSTANCE));
    }

    public SszSchema<SszList<SszUInt64>> getAttestingIndicesSchema() {
      return super.getFieldSchema0();
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
      SSZList<UInt64> attesting_indices, AttestationData data, BLSSignature signature) {
    super(
        SSZ_SCHEMA,
        SszUtils.toSszList(
            SSZ_SCHEMA.getAttestingIndicesSchema(), attesting_indices, SszUInt64::new),
        data,
        new SszSignature(signature));
  }

  public SSZList<UInt64> getAttesting_indices() {
    return new SSZBackingList<>(
        UInt64.class, getField0(), SszUInt64::new, AbstractSszPrimitive::get);
  }

  public AttestationData getData() {
    return getField1();
  }

  public BLSSignature getSignature() {
    return getField2().getSignature();
  }
}
