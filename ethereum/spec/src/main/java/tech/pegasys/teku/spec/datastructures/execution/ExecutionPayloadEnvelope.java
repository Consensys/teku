/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.spec.datastructures.execution;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.containers.Container7;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBit;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionRequests;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;

public class ExecutionPayloadEnvelope
    extends Container7<
        ExecutionPayloadEnvelope,
        ExecutionPayload,
        ExecutionRequests,
        SszUInt64,
        SszBytes32,
        SszList<SszKZGCommitment>,
        SszBit,
        SszBytes32> {

  ExecutionPayloadEnvelope(
      final ExecutionPayloadEnvelopeSchema schema,
      final ExecutionPayload payload,
      final ExecutionRequests executionRequests,
      final UInt64 builderIndex,
      final Bytes32 beaconBlockRoot,
      final SszList<SszKZGCommitment> blobKzgCommitments,
      final boolean payloadWithheld,
      final Bytes32 stateRoot) {
    super(
        schema,
        payload,
        executionRequests,
        SszUInt64.of(builderIndex),
        SszBytes32.of(beaconBlockRoot),
        blobKzgCommitments,
        SszBit.of(payloadWithheld),
        SszBytes32.of(stateRoot));
  }

  ExecutionPayloadEnvelope(final ExecutionPayloadEnvelopeSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public ExecutionPayload getPayload() {
    return getField0();
  }

  public ExecutionRequests getExecutionRequests() {
    return getField1();
  }

  public UInt64 getBuilderIndex() {
    return getField2().get();
  }

  public Bytes32 getBeaconBlockRoot() {
    return getField3().get();
  }

  public SszList<SszKZGCommitment> getBlobKzgCommitments() {
    return getField4();
  }

  public boolean isPayloadWithheld() {
    return getField5().get();
  }

  public Bytes32 getStateRoot() {
    return getField6().get();
  }

  public ExecutionPayloadEnvelope withStateRoot(final Bytes32 stateRoot) {
    return new ExecutionPayloadEnvelope(
        this.getSchema(),
        getPayload(),
        getExecutionRequests(),
        getBuilderIndex(),
        getBeaconBlockRoot(),
        getBlobKzgCommitments(),
        isPayloadWithheld(),
        stateRoot);
  }

  @Override
  public ExecutionPayloadEnvelopeSchema getSchema() {
    return (ExecutionPayloadEnvelopeSchema) super.getSchema();
  }
}
