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

package tech.pegasys.teku.spec.datastructures.state;

import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.ssz.containers.Container4;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema4;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitlistSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.util.config.Constants;

public class PendingAttestation
    extends Container4<PendingAttestation, SszBitlist, AttestationData, SszUInt64, SszUInt64> {

  public static class PendingAttestationSchema
      extends ContainerSchema4<
          PendingAttestation, SszBitlist, AttestationData, SszUInt64, SszUInt64> {

    public PendingAttestationSchema() {
      super(
          "PendingAttestation",
          namedSchema(
              "aggregation_bitfield",
              SszBitlistSchema.create(Constants.MAX_VALIDATORS_PER_COMMITTEE)),
          namedSchema("data", AttestationData.SSZ_SCHEMA),
          namedSchema("inclusion_delay", SszPrimitiveSchemas.UINT64_SCHEMA),
          namedSchema("proposer_index", SszPrimitiveSchemas.UINT64_SCHEMA));
    }

    public SszBitlistSchema<?> getAggregationBitfieldSchema() {
      return (SszBitlistSchema<?>) getFieldSchema0();
    }

    @Override
    public PendingAttestation createFromBackingNode(TreeNode node) {
      return new PendingAttestation(this, node);
    }
  }

  public static final PendingAttestationSchema SSZ_SCHEMA = new PendingAttestationSchema();

  private PendingAttestation(PendingAttestationSchema type, TreeNode backingNode) {
    super(type, backingNode);
  }

  public PendingAttestation(
      SszBitlist aggregation_bitfield,
      AttestationData data,
      UInt64 inclusion_delay,
      UInt64 proposer_index) {
    super(
        SSZ_SCHEMA,
        aggregation_bitfield,
        data,
        SszUInt64.of(inclusion_delay),
        SszUInt64.of(proposer_index));
  }

  public PendingAttestation() {
    super(SSZ_SCHEMA);
  }

  public PendingAttestation(PendingAttestation pendingAttestation) {
    super(SSZ_SCHEMA, pendingAttestation.getBackingNode());
  }

  public SszBitlist getAggregation_bits() {
    return getField0();
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
