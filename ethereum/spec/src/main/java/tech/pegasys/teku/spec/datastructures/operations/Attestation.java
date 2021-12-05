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

import com.google.common.collect.Sets;
import java.util.Collection;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.ssz.containers.Container3;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema3;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitlistSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.datastructures.type.SszSignatureSchema;
import tech.pegasys.teku.util.config.Constants;

public class Attestation
    extends Container3<Attestation, SszBitlist, AttestationData, SszSignature> {

  public static class AttestationSchema
      extends ContainerSchema3<Attestation, SszBitlist, AttestationData, SszSignature> {

    public AttestationSchema() {
      super(
          "Attestation",
          namedSchema(
              "aggregation_bits", SszBitlistSchema.create(Constants.MAX_VALIDATORS_PER_COMMITTEE)),
          namedSchema("data", AttestationData.SSZ_SCHEMA),
          namedSchema("signature", SszSignatureSchema.INSTANCE));
    }

    public SszBitlistSchema<?> getAggregationBitsSchema() {
      return (SszBitlistSchema<?>) getFieldSchema0();
    }

    @Override
    public Attestation createFromBackingNode(TreeNode node) {
      return new Attestation(this, node);
    }
  }

  public static final AttestationSchema SSZ_SCHEMA = new AttestationSchema();

  private Attestation(AttestationSchema type, TreeNode backingNode) {
    super(type, backingNode);
  }

  public Attestation(SszBitlist aggregation_bits, AttestationData data, BLSSignature signature) {
    super(SSZ_SCHEMA, aggregation_bits, data, new SszSignature(signature));
  }

  public Attestation() {
    super(SSZ_SCHEMA);
  }

  public static SszBitlist createEmptyAggregationBits() {
    return SSZ_SCHEMA.getAggregationBitsSchema().ofBits(Constants.MAX_VALIDATORS_PER_COMMITTEE);
  }

  public UInt64 getEarliestSlotForForkChoiceProcessing(final Spec spec) {
    return getData().getEarliestSlotForForkChoice(spec);
  }

  public Collection<Bytes32> getDependentBlockRoots() {
    return Sets.newHashSet(getData().getTarget().getRoot(), getData().getBeacon_block_root());
  }

  public SszBitlist getAggregationBits() {
    return getField0();
  }

  public AttestationData getData() {
    return getField1();
  }

  public BLSSignature getAggregateSignature() {
    return getField2().getSignature();
  }
}
