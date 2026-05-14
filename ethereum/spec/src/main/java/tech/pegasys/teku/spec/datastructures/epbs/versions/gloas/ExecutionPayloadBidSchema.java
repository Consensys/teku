/*
 * Copyright Consensys Software Inc., 2026
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
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.schema.SszContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;

public interface ExecutionPayloadBidSchema<T extends ExecutionPayloadBid>
    extends SszContainerSchema<T> {

  T create(
      Bytes32 parentBlockHash,
      Bytes32 parentBlockRoot,
      Bytes32 blockHash,
      Bytes32 prevRandao,
      Bytes20 feeRecipient,
      UInt64 gasLimit,
      UInt64 builderIndex,
      UInt64 slot,
      UInt64 value,
      UInt64 executionPayment,
      SszList<SszKZGCommitment> blobKzgCommitments,
      Bytes32 executionRequestsRoot);

  T createLocalSelfBuiltBid(
      Bytes32 parentBlockRoot,
      UInt64 slot,
      ExecutionPayload executionPayload,
      SszList<SszKZGCommitment> blobKzgCommitments,
      Bytes32 executionRequestsRoot);

  SszListSchema<SszKZGCommitment, ?> getBlobKzgCommitmentsSchema();

  @SuppressWarnings("unchecked")
  default ExecutionPayloadBidSchema<ExecutionPayloadBid> castTypeToExecutionPayloadBidSchema() {
    return (ExecutionPayloadBidSchema<ExecutionPayloadBid>) this;
  }
}
