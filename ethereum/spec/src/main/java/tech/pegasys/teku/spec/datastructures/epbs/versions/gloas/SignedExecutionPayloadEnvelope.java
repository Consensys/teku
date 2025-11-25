/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.spec.datastructures.epbs.versions.gloas;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.logging.LogFormatter;
import tech.pegasys.teku.infrastructure.ssz.containers.Container2;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;

public class SignedExecutionPayloadEnvelope
    extends Container2<SignedExecutionPayloadEnvelope, ExecutionPayloadEnvelope, SszSignature> {

  SignedExecutionPayloadEnvelope(
      final SignedExecutionPayloadEnvelopeSchema schema,
      final ExecutionPayloadEnvelope message,
      final BLSSignature signature) {
    super(schema, message, new SszSignature(signature));
  }

  SignedExecutionPayloadEnvelope(
      final SignedExecutionPayloadEnvelopeSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public ExecutionPayloadEnvelope getMessage() {
    return getField0();
  }

  public BLSSignature getSignature() {
    return getField1().getSignature();
  }

  @Override
  public SignedExecutionPayloadEnvelopeSchema getSchema() {
    return (SignedExecutionPayloadEnvelopeSchema) super.getSchema();
  }

  public UInt64 getSlot() {
    return getMessage().getSlot();
  }

  public Bytes32 getBeaconBlockRoot() {
    return getMessage().getBeaconBlockRoot();
  }

  public SlotAndBlockRoot getSlotAndBlockRoot() {
    return getMessage().getSlotAndBlockRoot();
  }

  public String toLogString() {
    return LogFormatter.formatExecutionPayload(
        getMessage().getSlot(), getMessage().getBeaconBlockRoot(), getMessage().getBuilderIndex());
  }
}
