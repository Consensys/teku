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

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.PowBlock;
import tech.pegasys.teku.ssz.type.Bytes8;

public class StubExecutionEngineChannel implements ExecutionEngineChannel {

  private Map<Bytes32, PowBlock> knownBlocks = new ConcurrentHashMap<>();

  public void addPowBlock(final PowBlock block) {
    knownBlocks.put(block.getBlockHash(), block);
  }

  @Override
  public SafeFuture<Optional<PowBlock>> getPowBlock(final Bytes32 blockHash) {
    return SafeFuture.completedFuture(Optional.ofNullable(knownBlocks.get(blockHash)));
  }

  @Override
  public SafeFuture<PowBlock> getPowChainHead() {
    return SafeFuture.failedFuture(
        new UnsupportedOperationException("getPowChainHead not supported"));
  }

  @Override
  public SafeFuture<Void> forkChoiceUpdated(
      final ForkChoiceState forkChoiceState, final Optional<PayloadAttributes> payloadAttributes) {
    return SafeFuture.COMPLETE;
  }

  @Override
  public SafeFuture<ExecutionPayload> getPayload(final Bytes8 payloadId) {
    return SafeFuture.failedFuture(new UnsupportedOperationException("getPayload not supported"));
  }

  @Override
  public SafeFuture<ExecutePayloadResult> executePayload(final ExecutionPayload executionPayload) {
    return SafeFuture.completedFuture(
        new ExecutePayloadResult(ExecutionPayloadStatus.VALID, Optional.empty(), Optional.empty()));
  }
}
