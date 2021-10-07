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
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthBlock.Block;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.events.ChannelInterface;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.ssz.type.Bytes20;

public interface ExecutionEngineChannel extends ChannelInterface {

  enum ExecutionPayloadStatus {
    VALID,
    INVALID,
    SYNCING
  }

  enum ConsensusValidationResult {
    VALID,
    INVALID
  }

  ExecutionEngineChannel NOOP =
      new ExecutionEngineChannel() {

        @Override
        public SafeFuture<UInt64> preparePayload(
            Bytes32 parentHash, UInt64 timestamp, Bytes32 random, Bytes20 feeRecipient) {
          return SafeFuture.completedFuture(UInt64.ZERO);
        }

        @Override
        public SafeFuture<ExecutionPayload> getPayload(UInt64 payloadId) {
          return SafeFuture.completedFuture(new ExecutionPayload());
        }

        @Override
        public SafeFuture<ExecutionPayloadStatus> executePayload(
            ExecutionPayload executionPayload) {
          return SafeFuture.completedFuture(ExecutionPayloadStatus.VALID);
        }

        @Override
        public SafeFuture<Void> forkChoiceUpdated(
            Bytes32 bestBlockHash, Bytes32 finalizedBlockHash) {
          return SafeFuture.completedFuture(null);
        }

        @Override
        public SafeFuture<Void> consensusValidated(
            Bytes32 blockHash, ConsensusValidationResult result) {
          return SafeFuture.completedFuture(null);
        }

        @Override
        public SafeFuture<Optional<Block>> getPowBlock(Bytes32 blockHash) {
          return SafeFuture.completedFuture(Optional.empty());
        }

        @Override
        public SafeFuture<Block> getPowChainHead() {
          throw new UnsupportedOperationException();
        }

        @Override
        public SafeFuture<Boolean> isFarBehindCurrentSlot() {
          return SafeFuture.completedFuture(false);
        }
      };

  SafeFuture<UInt64> preparePayload(
      Bytes32 parentHash, UInt64 timestamp, Bytes32 random, Bytes20 feeRecipient);

  SafeFuture<ExecutionPayload> getPayload(UInt64 payloadId);

  SafeFuture<ExecutionPayloadStatus> executePayload(ExecutionPayload executionPayload);

  SafeFuture<Void> forkChoiceUpdated(Bytes32 bestBlockHash, Bytes32 finalizedBlockHash);

  SafeFuture<Void> consensusValidated(Bytes32 blockHash, ConsensusValidationResult result);

  SafeFuture<Optional<Block>> getPowBlock(Bytes32 blockHash);

  SafeFuture<EthBlock.Block> getPowChainHead();

  SafeFuture<Boolean> isFarBehindCurrentSlot();
}
