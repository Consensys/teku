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
import tech.pegasys.teku.ssz.containers.Container4;
import tech.pegasys.teku.ssz.primitive.SszBytes32;
import tech.pegasys.teku.ssz.primitive.SszUInt64;
import tech.pegasys.teku.ssz.tree.TreeNode;

public class SyncCommitteeSignature
    extends Container4<SyncCommitteeSignature, SszUInt64, SszBytes32, SszUInt64, SszSignature> {

  SyncCommitteeSignature(final SyncCommitteeSignatureSchema schema, final TreeNode backingNode) {
    super(schema, backingNode);
  }

  public SyncCommitteeSignature(
      final SyncCommitteeSignatureSchema schema,
      final SszUInt64 slot,
      final SszBytes32 beaconBlockRoot,
      final SszUInt64 validatorIndex,
      final SszSignature signature) {
    super(schema, slot, beaconBlockRoot, validatorIndex, signature);
  }

  public UInt64 getSlot() {
    return getField0().get();
  }

  public Bytes32 getBeaconBlockRoot() {
    return getField1().get();
  }

  public UInt64 getValidatorIndex() {
    return getField2().get();
  }

  public BLSSignature getSignature() {
    return getField3().getSignature();
  }

  @Override
  public SyncCommitteeSignatureSchema getSchema() {
    return (SyncCommitteeSignatureSchema) super.getSchema();
  }
}
