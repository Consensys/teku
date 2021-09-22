/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.services.powchain.execution.client;

import java.util.Arrays;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.web3j.protocol.core.methods.response.EthBlock;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.services.powchain.execution.client.schema.ExecutePayloadResponse;
import tech.pegasys.teku.services.powchain.execution.client.schema.ExecutionPayload;
import tech.pegasys.teku.services.powchain.execution.client.schema.GenericResponse;
import tech.pegasys.teku.services.powchain.execution.client.schema.PreparePayloadRequest;
import tech.pegasys.teku.services.powchain.execution.client.schema.PreparePayloadResponse;
import tech.pegasys.teku.services.powchain.execution.client.schema.Response;
import tech.pegasys.teku.spec.executionengine.ExecutionEngineChannel;
import tech.pegasys.teku.ssz.type.Bytes20;

public interface ExecutionEngineClient {

  SafeFuture<Response<PreparePayloadResponse>> preparePayload(PreparePayloadRequest request);

  SafeFuture<Response<ExecutionPayload>> getPayload(UInt64 payloadId);

  SafeFuture<Response<ExecutePayloadResponse>> executePayload(ExecutionPayload request);

  SafeFuture<Response<GenericResponse>> consensusSetHead(Bytes32 blockHash);

  SafeFuture<Response<GenericResponse>> consensusFinalizeBlock(Bytes32 blockHash);

  SafeFuture<Optional<EthBlock.Block>> getPowBlock(Bytes32 blockHash);

  SafeFuture<EthBlock.Block> getPowChainHead();

  ExecutionEngineClient Stub =
      new ExecutionEngineClient() {
        private final Bytes ZERO_LOGS_BLOOM = Bytes.wrap(new byte[256]);
        private UInt64 number = UInt64.ZERO;
        private UInt64 payloadId = UInt64.ZERO;
        private Optional<PreparePayloadRequest> lastPreparePayloadRequest = Optional.empty();

        @Override
        public SafeFuture<Response<PreparePayloadResponse>> preparePayload(
            PreparePayloadRequest request) {
          lastPreparePayloadRequest = Optional.of(request);
          payloadId = payloadId.increment();
          return SafeFuture.completedFuture(new Response<>(new PreparePayloadResponse(payloadId)));
        }

        @Override
        public SafeFuture<Response<ExecutionPayload>> getPayload(UInt64 payloadId) {
          PreparePayloadRequest preparePayloadRequest =
              lastPreparePayloadRequest.orElseThrow(
                  () -> new IllegalStateException("preparePayload was not called."));
          number = number.increment();
          return SafeFuture.completedFuture(
              new Response<>(
                  new ExecutionPayload(
                      preparePayloadRequest.parentHash,
                      Bytes20.ZERO,
                      Bytes32.ZERO,
                      Bytes32.ZERO,
                      ZERO_LOGS_BLOOM,
                      Bytes32.ZERO,
                      number,
                      UInt64.ZERO,
                      UInt64.ZERO,
                      preparePayloadRequest.timestamp,
                      Bytes32.ZERO,
                      Bytes32.random(),
                      Arrays.asList(Bytes.random(128), Bytes.random(256), Bytes.random(512)))));
        }

        @Override
        public SafeFuture<Response<ExecutePayloadResponse>> executePayload(
            ExecutionPayload request) {
          return SafeFuture.completedFuture(
              new Response<>(
                  new ExecutePayloadResponse(
                      ExecutionEngineChannel.ExecutionPayloadStatus.VALID.name())));
        }

        @Override
        public SafeFuture<Response<GenericResponse>> consensusSetHead(Bytes32 blockHash) {
          return SafeFuture.completedFuture(new Response<>(new GenericResponse(true)));
        }

        @Override
        public SafeFuture<Response<GenericResponse>> consensusFinalizeBlock(Bytes32 blockHash) {
          return SafeFuture.completedFuture(new Response<>(new GenericResponse(true)));
        }

        @Override
        public SafeFuture<Optional<EthBlock.Block>> getPowBlock(Bytes32 blockHash) {
          return SafeFuture.completedFuture(Optional.empty());
        }

        @Override
        public SafeFuture<EthBlock.Block> getPowChainHead() {
          return SafeFuture.completedFuture(new EthBlock.Block());
        }
      };
}
