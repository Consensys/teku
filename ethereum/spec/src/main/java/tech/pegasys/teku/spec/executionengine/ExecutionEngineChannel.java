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

package tech.pegasys.teku.spec.executionengine;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.events.ChannelInterface;
import tech.pegasys.teku.infrastructure.ssz.type.Bytes8;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.PowBlock;

public interface ExecutionEngineChannel extends ChannelInterface {
  ExecutionEngineChannel NOOP =
      new ExecutionEngineChannel() {
        @Override
        public SafeFuture<Optional<PowBlock>> getPowBlock(final Bytes32 blockHash) {
          return SafeFuture.completedFuture(Optional.empty());
        }

        @Override
        public SafeFuture<PowBlock> getPowChainHead() {
          throw new UnsupportedOperationException();
        }

        @Override
        public SafeFuture<ForkChoiceUpdatedResult> forkChoiceUpdated(
            final ForkChoiceState forkChoiceState,
            final Optional<PayloadAttributes> payloadAttributes) {
          return SafeFuture.completedFuture(null);
        }

        @Override
        public SafeFuture<ExecutionPayload> getPayload(final Bytes8 payloadId, final UInt64 slot) {
          return SafeFuture.completedFuture(null);
        }

        @Override
        public SafeFuture<ExecutePayloadResult> executePayload(
            final ExecutionPayload executionPayload) {
          return SafeFuture.completedFuture(ExecutePayloadResult.SYNCING);
        }
      };

  SafeFuture<Optional<PowBlock>> getPowBlock(final Bytes32 blockHash);

  SafeFuture<PowBlock> getPowChainHead();

  SafeFuture<ForkChoiceUpdatedResult> forkChoiceUpdated(
      final ForkChoiceState forkChoiceState, final Optional<PayloadAttributes> payloadAttributes);

  SafeFuture<ExecutionPayload> getPayload(final Bytes8 payloadId, final UInt64 slot);

  SafeFuture<ExecutePayloadResult> executePayload(final ExecutionPayload executionPayload);
}
