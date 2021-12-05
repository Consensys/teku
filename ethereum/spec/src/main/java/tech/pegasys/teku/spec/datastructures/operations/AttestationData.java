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

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.containers.Container5;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema5;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;

public class AttestationData
    extends Container5<AttestationData, SszUInt64, SszUInt64, SszBytes32, Checkpoint, Checkpoint> {

  public static class AttestationDataSchema
      extends ContainerSchema5<
          AttestationData, SszUInt64, SszUInt64, SszBytes32, Checkpoint, Checkpoint> {

    public AttestationDataSchema() {
      super(
          "AttestationData",
          namedSchema("slot", SszPrimitiveSchemas.UINT64_SCHEMA),
          namedSchema("index", SszPrimitiveSchemas.UINT64_SCHEMA),
          namedSchema("beacon_block_root", SszPrimitiveSchemas.BYTES32_SCHEMA),
          namedSchema("source", Checkpoint.SSZ_SCHEMA),
          namedSchema("target", Checkpoint.SSZ_SCHEMA));
    }

    @Override
    public AttestationData createFromBackingNode(TreeNode node) {
      return new AttestationData(this, node);
    }
  }

  public static final AttestationDataSchema SSZ_SCHEMA = new AttestationDataSchema();

  private AttestationData(AttestationDataSchema type, TreeNode backingNode) {
    super(type, backingNode);
  }

  public AttestationData(
      UInt64 slot, UInt64 index, Bytes32 beacon_block_root, Checkpoint source, Checkpoint target) {
    super(
        SSZ_SCHEMA,
        SszUInt64.of(slot),
        SszUInt64.of(index),
        SszBytes32.of(beacon_block_root),
        source,
        target);
  }

  public AttestationData(UInt64 slot, AttestationData data) {
    this(slot, data.getIndex(), data.getBeacon_block_root(), data.getSource(), data.getTarget());
  }

  public UInt64 getEarliestSlotForForkChoice(final Spec spec) {
    // Attestations can't be processed by fork choice until their slot is in the past and until we
    // are in the same epoch as their target.
    return getSlot().plus(UInt64.ONE).max(getTarget().getEpochStartSlot(spec));
  }

  public UInt64 getSlot() {
    return getField0().get();
  }

  public UInt64 getIndex() {
    return getField1().get();
  }

  public Bytes32 getBeacon_block_root() {
    return getField2().get();
  }

  public Checkpoint getSource() {
    return getField3();
  }

  public Checkpoint getTarget() {
    return getField4();
  }
}
