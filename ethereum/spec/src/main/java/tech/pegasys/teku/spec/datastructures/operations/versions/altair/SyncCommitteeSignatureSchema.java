/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.spec.datastructures.operations.versions.altair;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.datastructures.type.SszSignatureSchema;
import tech.pegasys.teku.ssz.containers.ContainerSchema4;
import tech.pegasys.teku.ssz.primitive.SszBytes32;
import tech.pegasys.teku.ssz.primitive.SszUInt64;
import tech.pegasys.teku.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.ssz.tree.TreeNode;

public class SyncCommitteeSignatureSchema
    extends ContainerSchema4<
        SyncCommitteeSignature, SszUInt64, SszBytes32, SszUInt64, SszSignature> {

  public static final SyncCommitteeSignatureSchema INSTANCE = new SyncCommitteeSignatureSchema();

  private SyncCommitteeSignatureSchema() {
    super(
        "SyncCommitteeSignature",
        namedSchema("slot", SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema("beacon_block_root", SszPrimitiveSchemas.BYTES32_SCHEMA),
        namedSchema("validator_index", SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema("signature", SszSignatureSchema.INSTANCE));
  }

  @Override
  public SyncCommitteeSignature createFromBackingNode(final TreeNode node) {
    return new SyncCommitteeSignature(this, node);
  }

  public SyncCommitteeSignature create(
      final UInt64 slot,
      final Bytes32 beaconBlockRoot,
      final UInt64 validatorIndex,
      final BLSSignature signature) {
    return new SyncCommitteeSignature(
        this,
        SszUInt64.of(slot),
        SszBytes32.of(beaconBlockRoot),
        SszUInt64.of(validatorIndex),
        new SszSignature(signature));
  }
}
