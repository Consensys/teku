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

package tech.pegasys.teku.spec.executionengine.client;

import java.util.Arrays;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.executionengine.client.schema.AssembleBlockRequest;
import tech.pegasys.teku.spec.executionengine.client.schema.ExecutionPayload;
import tech.pegasys.teku.spec.executionengine.client.schema.GenericResponse;
import tech.pegasys.teku.spec.executionengine.client.schema.NewBlockResponse;
import tech.pegasys.teku.spec.executionengine.client.schema.Response;
import tech.pegasys.teku.ssz.type.Bytes20;

public interface ExecutionEngineClient {

  SafeFuture<Response<ExecutionPayload>> consensusAssembleBlock(AssembleBlockRequest request);

  SafeFuture<Response<NewBlockResponse>> consensusNewBlock(ExecutionPayload request);

  SafeFuture<Response<GenericResponse>> consensusSetHead(Bytes32 blockHash);

  SafeFuture<Response<GenericResponse>> consensusFinalizeBlock(Bytes32 blockHash);

  ExecutionEngineClient Stub =
      new ExecutionEngineClient() {
        private final Bytes ZERO_LOGS_BLOOM = Bytes.wrap(new byte[256]);
        private UInt64 number = UInt64.ZERO;

        @Override
        public SafeFuture<Response<ExecutionPayload>> consensusAssembleBlock(
            AssembleBlockRequest request) {
          number = number.increment();
          return SafeFuture.completedFuture(
              new Response<>(
                  new ExecutionPayload(
                      Bytes32.random(),
                      request.parentHash,
                      Bytes20.ZERO,
                      Bytes32.ZERO,
                      number,
                      UInt64.ZERO,
                      UInt64.ZERO,
                      request.timestamp,
                      Bytes32.ZERO,
                      ZERO_LOGS_BLOOM,
                      Arrays.asList(Bytes.random(128), Bytes.random(256), Bytes.random(512)))));
        }

        @Override
        public SafeFuture<Response<NewBlockResponse>> consensusNewBlock(ExecutionPayload request) {
          return SafeFuture.completedFuture(new Response<>(new NewBlockResponse(true)));
        }

        @Override
        public SafeFuture<Response<GenericResponse>> consensusSetHead(Bytes32 blockHash) {
          return SafeFuture.completedFuture(new Response<>(new GenericResponse(true)));
        }

        @Override
        public SafeFuture<Response<GenericResponse>> consensusFinalizeBlock(Bytes32 blockHash) {
          return SafeFuture.completedFuture(new Response<>(new GenericResponse(true)));
        }
      };
}
