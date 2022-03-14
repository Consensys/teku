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

package tech.pegasys.teku.ethereum.executionlayer.client;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.ExecutionPayloadHeaderV1;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.ExecutionPayloadV1;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.ForkChoiceStateV1;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.ForkChoiceUpdatedResult;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.PayloadAttributesV1;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.PayloadStatusV1;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.Response;
import tech.pegasys.teku.ethereum.executionlayer.client.schema.TransitionConfigurationV1;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes8;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.PowBlock;

public interface ExecutionEngineClient {
  // eth namespace
  SafeFuture<Optional<PowBlock>> getPowBlock(Bytes32 blockHash);

  SafeFuture<PowBlock> getPowChainHead();

  // engine namespace
  SafeFuture<Response<ExecutionPayloadV1>> getPayload(Bytes8 payloadId);

  SafeFuture<Response<PayloadStatusV1>> newPayload(ExecutionPayloadV1 executionPayload);

  SafeFuture<Response<ForkChoiceUpdatedResult>> forkChoiceUpdated(
      ForkChoiceStateV1 forkChoiceState, Optional<PayloadAttributesV1> payloadAttributes);

  SafeFuture<Response<TransitionConfigurationV1>> exchangeTransitionConfiguration(
      TransitionConfigurationV1 transitionConfiguration);

  // builder namespace
  SafeFuture<Response<ExecutionPayloadHeaderV1>> getPayloadHeader(Bytes8 payloadId);

  SafeFuture<Response<ExecutionPayloadV1>> proposeBlindedBlock(
      SignedBeaconBlock signedBlindedBeaconBlock);
}
