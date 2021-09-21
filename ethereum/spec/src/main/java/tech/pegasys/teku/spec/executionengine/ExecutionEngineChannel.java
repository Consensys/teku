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

public interface ExecutionEngineChannel extends ChannelInterface {

  ExecutionEngineChannel NOOP =
      new ExecutionEngineChannel() {

        @Override
        public SafeFuture<Void> prepareBlock(
            Bytes32 parentHash, UInt64 timestamp, UInt64 payloadId) {
          return SafeFuture.completedFuture(null);
        }

        @Override
        public SafeFuture<ExecutionPayload> assembleBlock(Bytes32 parentHash, UInt64 timestamp) {
          return SafeFuture.completedFuture(new ExecutionPayload());
        }

        @Override
        public SafeFuture<Boolean> newBlock(ExecutionPayload executionPayload) {
          return SafeFuture.completedFuture(true);
        }

        @Override
        public SafeFuture<Void> setHead(Bytes32 blockHash) {
          return SafeFuture.completedFuture(null);
        }

        @Override
        public SafeFuture<Void> finalizeBlock(Bytes32 blockHash) {
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
      };

  SafeFuture<Void> prepareBlock(Bytes32 parentHash, UInt64 timestamp, UInt64 payloadId);

  /**
   * Requests execution-engine to produce a block.
   *
   * @param parentHash the hash of execution block to produce atop of
   * @param timestamp the timestamp of the beginning of the slot
   * @return a response with execution payload
   */
  SafeFuture<ExecutionPayload> assembleBlock(Bytes32 parentHash, UInt64 timestamp);

  /**
   * Requests execution-engine to process a block.
   *
   * @param executionPayload an executable payload
   * @return {@code true} if processing succeeded, {@code false} otherwise
   */
  SafeFuture<Boolean> newBlock(ExecutionPayload executionPayload);

  SafeFuture<Void> setHead(Bytes32 blockHash);

  SafeFuture<Void> finalizeBlock(Bytes32 blockHash);

  SafeFuture<Optional<Block>> getPowBlock(Bytes32 blockHash);

  SafeFuture<EthBlock.Block> getPowChainHead();
}
