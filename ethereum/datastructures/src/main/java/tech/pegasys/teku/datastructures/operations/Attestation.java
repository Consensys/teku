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

import com.google.common.collect.Sets;
import java.util.Collection;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.Bitlist;
import tech.pegasys.teku.ssz.backing.SszList;
import tech.pegasys.teku.ssz.backing.SszVector;
import tech.pegasys.teku.ssz.backing.containers.Container3;
import tech.pegasys.teku.ssz.backing.containers.ContainerSchema3;
import tech.pegasys.teku.ssz.backing.schema.SszComplexSchemas;
import tech.pegasys.teku.ssz.backing.schema.SszComplexSchemas.SszBitListSchema;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszBit;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszByte;
import tech.pegasys.teku.ssz.backing.view.SszUtils;
import tech.pegasys.teku.util.config.Constants;

public class Attestation
    extends Container3<Attestation, SszList<SszBit>, AttestationData, SszVector<SszByte>> {

  public static class AttestationSchema
      extends ContainerSchema3<Attestation, SszList<SszBit>, AttestationData, SszVector<SszByte>> {

    public AttestationSchema() {
      super(
          "Attestation",
          namedSchema(
              "aggregation_bits", new SszBitListSchema(Constants.MAX_VALIDATORS_PER_COMMITTEE)),
          namedSchema("data", AttestationData.SSZ_SCHEMA),
          namedSchema("signature", SszComplexSchemas.BYTES_96_SCHEMA));
    }

    public SszBitListSchema getAggregationBitsSchema() {
      return (SszBitListSchema) getFieldSchema0();
    }

    @Override
    public Attestation createFromBackingNode(TreeNode node) {
      return new Attestation(this, node);
    }
  }

  public static final AttestationSchema SSZ_SCHEMA = new AttestationSchema();

  private Bitlist aggregationBitsCache;
  private BLSSignature signatureCache;

  private Attestation(AttestationSchema type, TreeNode backingNode) {
    super(type, backingNode);
  }

  public Attestation(Bitlist aggregation_bits, AttestationData data, BLSSignature signature) {
    super(
        SSZ_SCHEMA,
        SszUtils.toSszBitList(SSZ_SCHEMA.getAggregationBitsSchema(), aggregation_bits),
        data,
        SszUtils.toSszByteVector(signature.toBytesCompressed()));
    aggregationBitsCache = aggregation_bits;
    signatureCache = signature;
  }

  public Attestation() {
    super(SSZ_SCHEMA);
  }

  public static Bitlist createEmptyAggregationBits() {
    return new Bitlist(
        Constants.MAX_VALIDATORS_PER_COMMITTEE, Constants.MAX_VALIDATORS_PER_COMMITTEE);
  }

  public UInt64 getEarliestSlotForForkChoiceProcessing() {
    return getData().getEarliestSlotForForkChoice();
  }

  public Collection<Bytes32> getDependentBlockRoots() {
    return Sets.newHashSet(getData().getTarget().getRoot(), getData().getBeacon_block_root());
  }

  public Bitlist getAggregation_bits() {
    if (aggregationBitsCache == null) {
      aggregationBitsCache = SszUtils.getBitlist(getField0());
    }
    return aggregationBitsCache;
  }

  public AttestationData getData() {
    return getField1();
  }

  public BLSSignature getAggregate_signature() {
    if (signatureCache == null) {
      signatureCache = BLSSignature.fromBytesCompressed(SszUtils.getAllBytes(getField2()));
    }
    return signatureCache;
  }
}
