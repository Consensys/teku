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

package tech.pegasys.teku.datastructures.operations;

import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.backing.SszVector;
import tech.pegasys.teku.ssz.backing.containers.Container3;
import tech.pegasys.teku.ssz.backing.containers.ContainerType3;
import tech.pegasys.teku.ssz.backing.schema.SszComplexSchemas;
import tech.pegasys.teku.ssz.backing.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszByte;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszUInt64;
import tech.pegasys.teku.ssz.backing.view.SszUtils;

public class AggregateAndProof
    extends Container3<AggregateAndProof, SszUInt64, Attestation, SszVector<SszByte>> {

  public static class AggregateAndProofType
      extends ContainerType3<AggregateAndProof, SszUInt64, Attestation, SszVector<SszByte>> {

    public AggregateAndProofType() {
      super(
          "AggregateAndProof",
          namedSchema("index", SszPrimitiveSchemas.UINT64_SCHEMA),
          namedSchema("aggregate", Attestation.TYPE),
          namedSchema("selection_proof", SszComplexSchemas.BYTES_96_SCHEMA));
    }

    @Override
    public AggregateAndProof createFromBackingNode(TreeNode node) {
      return new AggregateAndProof(this, node);
    }
  }

  public static final AggregateAndProofType TYPE = new AggregateAndProofType();

  private BLSSignature selectionProofCache;

  private AggregateAndProof(AggregateAndProofType type, TreeNode backingNode) {
    super(type, backingNode);
  }

  public AggregateAndProof(UInt64 index, Attestation aggregate, BLSSignature selection_proof) {
    super(
        TYPE,
        new SszUInt64(index),
        aggregate,
        SszUtils.toSszByteVector(selection_proof.toBytesCompressed()));
    selectionProofCache = selection_proof;
  }

  public UInt64 getIndex() {
    return getField0().get();
  }

  public Attestation getAggregate() {
    return getField1();
  }

  public BLSSignature getSelection_proof() {
    if (selectionProofCache == null) {
      selectionProofCache = BLSSignature.fromBytesCompressed(SszUtils.getAllBytes(getField2()));
    }
    return selectionProofCache;
  }
}
