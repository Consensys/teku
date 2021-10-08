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
import org.web3j.protocol.core.methods.response.EthBlock.Block;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.ssz.type.Bytes20;

public class OptimisticSyncExecutionEngineChannel implements ExecutionEngineChannel {

  private final ExecutionEngineChannel delegate;

  public OptimisticSyncExecutionEngineChannel(ExecutionEngineChannel delegate) {
    this.delegate = delegate;
  }

  @Override
  public SafeFuture<UInt64> preparePayload(
      Bytes32 parentHash, UInt64 timestamp, Bytes32 random, Bytes20 feeRecipient) {
    return delegate.preparePayload(parentHash, timestamp, random, feeRecipient);
  }

  @Override
  public SafeFuture<ExecutionPayload> getPayload(UInt64 payloadId) {
    return delegate.getPayload(payloadId);
  }

  @Override
  public SafeFuture<ExecutionPayloadStatus> executePayload(ExecutionPayload executionPayload) {
    return delegate
        .executePayload(executionPayload)
        .thenApply(
            res -> res == ExecutionPayloadStatus.SYNCING ? ExecutionPayloadStatus.VALID : res);
  }

  @Override
  public SafeFuture<Void> forkChoiceUpdated(Bytes32 bestBlockHash, Bytes32 finalizedBlockHash) {
    return delegate.forkChoiceUpdated(bestBlockHash, finalizedBlockHash);
  }

  @Override
  public SafeFuture<Void> consensusValidated(Bytes32 blockHash, ConsensusValidationResult result) {
    return delegate.consensusValidated(blockHash, result);
  }

  @Override
  public SafeFuture<Optional<Block>> getPowBlock(Bytes32 blockHash) {
    return delegate.getPowBlock(blockHash);
  }

  @Override
  public SafeFuture<Block> getPowChainHead() {
    return delegate.getPowChainHead();
  }

  @Override
  public SafeFuture<Boolean> isFarBehindCurrentSlot() {
    return SafeFuture.completedFuture(true);
  }
}
