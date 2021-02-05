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

package tech.pegasys.teku.datastructures.state;

import tech.pegasys.teku.datastructures.operations.AttestationData;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.Bitlist;
import tech.pegasys.teku.ssz.backing.SszList;
import tech.pegasys.teku.ssz.backing.containers.Container4;
import tech.pegasys.teku.ssz.backing.containers.ContainerType4;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.schema.SszComplexSchemas.SszBitListSchema;
import tech.pegasys.teku.ssz.backing.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszBit;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszUInt64;
import tech.pegasys.teku.ssz.backing.view.SszUtils;
import tech.pegasys.teku.util.config.Constants;

public class PendingAttestation
    extends Container4<PendingAttestation, SszList<SszBit>, AttestationData, SszUInt64, SszUInt64> {

  public static class PendingAttestationType
      extends ContainerType4<
          PendingAttestation, SszList<SszBit>, AttestationData, SszUInt64, SszUInt64> {

    public PendingAttestationType() {
      super(
          "PendingAttestation",
          namedSchema(
              "aggregation_bitfield", new SszBitListSchema(Constants.MAX_VALIDATORS_PER_COMMITTEE)),
          namedSchema("data", AttestationData.TYPE),
          namedSchema("inclusion_delay", SszPrimitiveSchemas.UINT64_SCHEMA),
          namedSchema("proposer_index", SszPrimitiveSchemas.UINT64_SCHEMA));
    }

    public SszBitListSchema getAggregationBitfieldType() {
      return (SszBitListSchema) getFieldType0();
    }

    @Override
    public PendingAttestation createFromBackingNode(TreeNode node) {
      return new PendingAttestation(this, node);
    }
  }

  public static final PendingAttestationType TYPE = new PendingAttestationType();

  private PendingAttestation(PendingAttestationType type, TreeNode backingNode) {
    super(type, backingNode);
  }

  public PendingAttestation(
      Bitlist aggregation_bitfield,
      AttestationData data,
      UInt64 inclusion_delay,
      UInt64 proposer_index) {
    super(
        TYPE,
        SszUtils.createBitlistView(TYPE.getAggregationBitfieldType(), aggregation_bitfield),
        data,
        new SszUInt64(inclusion_delay),
        new SszUInt64(proposer_index));
  }

  public PendingAttestation() {
    super(TYPE);
  }

  public PendingAttestation(PendingAttestation pendingAttestation) {
    super(TYPE, pendingAttestation.getBackingNode());
  }

  public Bitlist getAggregation_bits() {
    return SszUtils.getBitlist(getField0());
  }

  public AttestationData getData() {
    return getField1();
  }

  public UInt64 getInclusion_delay() {
    return getField2().get();
  }

  public UInt64 getProposer_index() {
    return getField3().get();
  }
}
