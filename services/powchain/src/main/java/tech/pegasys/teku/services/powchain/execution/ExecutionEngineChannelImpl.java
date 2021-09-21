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

package tech.pegasys.teku.services.powchain.execution;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.errorprone.annotations.FormatMethod;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.web3j.protocol.core.methods.response.EthBlock.Block;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.logging.ColorConsolePrinter;
import tech.pegasys.teku.infrastructure.logging.ColorConsolePrinter.Color;
import tech.pegasys.teku.infrastructure.logging.LogFormatter;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.services.powchain.execution.client.ExecutionEngineClient;
import tech.pegasys.teku.services.powchain.execution.client.Web3JExecutionEngineClient;
import tech.pegasys.teku.services.powchain.execution.client.schema.AssembleBlockRequest;
import tech.pegasys.teku.services.powchain.execution.client.schema.ExecutionPayload;
import tech.pegasys.teku.services.powchain.execution.client.schema.NewBlockResponse;
import tech.pegasys.teku.services.powchain.execution.client.schema.Response;
import tech.pegasys.teku.spec.executionengine.ExecutionEngineChannel;

public class ExecutionEngineChannelImpl implements ExecutionEngineChannel {

  private static final Logger LOG = LogManager.getLogger();

  private final ExecutionEngineClient executionEngineClient;

  public static ExecutionEngineChannelImpl create(String eth1EngineEndpoint) {
    checkNotNull(eth1EngineEndpoint);
    return new ExecutionEngineChannelImpl(new Web3JExecutionEngineClient(eth1EngineEndpoint));
  }

  public static ExecutionEngineChannelImpl createStub() {
    return new ExecutionEngineChannelImpl(ExecutionEngineClient.Stub);
  }

  public ExecutionEngineChannelImpl(ExecutionEngineClient executionEngineClient) {
    this.executionEngineClient = executionEngineClient;
  }

  private static <K> K unwrapResponseOrThrow(Response<K> response) {
    checkArgument(
        response.getPayload() != null, "Invalid remote response: %s", response.getReason());
    return response.getPayload();
  }

  @FormatMethod
  private static void printConsole(String formatString, Object... args) {
    LOG.info(ColorConsolePrinter.print(String.format(formatString, args), Color.CYAN));
  }

  @Override
  public SafeFuture<Void> prepareBlock(Bytes32 parentHash, UInt64 timestamp, UInt64 payloadId) {
    return SafeFuture.completedFuture(null);
  }

  @Override
  public SafeFuture<tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload> assembleBlock(
      Bytes32 parentHash, UInt64 timestamp) {
    AssembleBlockRequest request = new AssembleBlockRequest(parentHash, timestamp);

    return executionEngineClient
        .consensusAssembleBlock(request)
        .thenApply(ExecutionEngineChannelImpl::unwrapResponseOrThrow)
        .thenApply(ExecutionPayload::asInternalExecutionPayload)
        .thenPeek(
            executionPayload ->
                printConsole(
                    "consensus_assembleBlock(parent_hash=%s, timestamp=%s) ~> %s",
                    LogFormatter.formatHashRoot(parentHash), timestamp, executionPayload));
  }

  @Override
  public SafeFuture<Boolean> newBlock(
      tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload executionPayload) {
    return executionEngineClient
        .consensusNewBlock(new ExecutionPayload(executionPayload))
        .thenApply(ExecutionEngineChannelImpl::unwrapResponseOrThrow)
        .thenApply(NewBlockResponse::getValid)
        .thenPeek(
            res ->
                printConsole(
                    "Failed consensus_newBlock(execution_payload=%s), reason: %s",
                    executionPayload, res));
  }

  @Override
  public SafeFuture<Void> forkChoiceUpdated(Bytes32 bestBlockHash, Bytes32 finalizedBlockHash) {
    return executionEngineClient
        .forkChoiceUpdated(bestBlockHash, finalizedBlockHash)
        .thenApply(ExecutionEngineChannelImpl::unwrapResponseOrThrow)
        .thenPeek(
            __ ->
                printConsole(
                    "engine_forkchoiceUpdated(bestBlockHash=%s, finalizedBlockHash=%s)",
                    LogFormatter.formatHashRoot(bestBlockHash),
                    LogFormatter.formatHashRoot(finalizedBlockHash)))
        .thenApply(__ -> null);
  }

  @Override
  public SafeFuture<Optional<Block>> getPowBlock(Bytes32 blockHash) {
    return executionEngineClient
        .getPowBlock(blockHash)
        .thenPeek(
            res ->
                res.ifPresentOrElse(
                    block ->
                        printConsole(
                            "eth_getBlock(blockHash=%s) ~> EthBlock(number=%s, totalDifficulty=%s, difficulty=%s)",
                            LogFormatter.formatHashRoot(blockHash),
                            block.getNumber().toString(),
                            block.getTotalDifficulty().toString(),
                            block.getDifficulty().toString()),
                    () ->
                        printConsole(
                            "eth_getBlock(blockHash=%s) ~> null",
                            LogFormatter.formatHashRoot(blockHash))));
  }

  @Override
  public SafeFuture<Block> getPowChainHead() {
    return executionEngineClient
        .getPowChainHead()
        .thenPeek(
            block ->
                printConsole(
                    "eth_getLatestBlock() ~> EthBlock(blockHash=%s, number=%s, totalDifficulty=%s, difficulty=%s)",
                    LogFormatter.formatHashRoot(Bytes32.fromHexString(block.getHash())),
                    block.getNumber().toString(),
                    block.getTotalDifficulty().toString(),
                    block.getDifficulty().toString()));
  }
}
