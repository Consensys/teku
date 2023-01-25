/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.ethereum.executionclient;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.executionclient.schema.BlobsBundleV1;
import tech.pegasys.teku.ethereum.executionclient.schema.ExecutionPayloadV1;
import tech.pegasys.teku.ethereum.executionclient.schema.ForkChoiceStateV1;
import tech.pegasys.teku.ethereum.executionclient.schema.ForkChoiceUpdatedResult;
import tech.pegasys.teku.ethereum.executionclient.schema.GetPayloadV2Response;
import tech.pegasys.teku.ethereum.executionclient.schema.GetPayloadV3Response;
import tech.pegasys.teku.ethereum.executionclient.schema.PayloadAttributesV1;
import tech.pegasys.teku.ethereum.executionclient.schema.PayloadStatusV1;
import tech.pegasys.teku.ethereum.executionclient.schema.Response;
import tech.pegasys.teku.ethereum.executionclient.schema.TransitionConfigurationV1;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes8;
import tech.pegasys.teku.spec.datastructures.execution.PowBlock;

public interface ExecutionEngineClient {
  // eth namespace
  SafeFuture<Optional<PowBlock>> getPowBlock(Bytes32 blockHash);

  SafeFuture<PowBlock> getPowChainHead();

  // engine namespace
  SafeFuture<Response<ExecutionPayloadV1>> getPayloadV1(Bytes8 payloadId);

  SafeFuture<Response<GetPayloadV2Response>> getPayloadV2(Bytes8 payloadId);

  SafeFuture<Response<GetPayloadV3Response>> getPayloadV3(Bytes8 payloadId);

  SafeFuture<Response<BlobsBundleV1>> getBlobsBundleV1(Bytes8 payloadId);

  SafeFuture<Response<PayloadStatusV1>> newPayloadV1(ExecutionPayloadV1 executionPayload);

  SafeFuture<Response<PayloadStatusV1>> newPayloadV2(ExecutionPayloadV1 executionPayload);

  SafeFuture<Response<PayloadStatusV1>> newPayloadV3(ExecutionPayloadV1 executionPayload);

  SafeFuture<Response<ForkChoiceUpdatedResult>> forkChoiceUpdatedV1(
      ForkChoiceStateV1 forkChoiceState, Optional<PayloadAttributesV1> payloadAttributes);

  SafeFuture<Response<ForkChoiceUpdatedResult>> forkChoiceUpdatedV2(
      ForkChoiceStateV1 forkChoiceState, Optional<PayloadAttributesV1> payloadAttributes);

  SafeFuture<Response<TransitionConfigurationV1>> exchangeTransitionConfiguration(
      TransitionConfigurationV1 transitionConfiguration);
}
