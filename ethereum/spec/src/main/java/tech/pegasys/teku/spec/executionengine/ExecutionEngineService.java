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

package tech.pegasys.teku.spec.executionengine;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.concurrent.ExecutionException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.logging.ColorConsolePrinter;
import tech.pegasys.teku.infrastructure.logging.ColorConsolePrinter.Color;
import tech.pegasys.teku.infrastructure.logging.LogFormatter;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSchema;
import tech.pegasys.teku.spec.executionengine.client.ExecutionEngineClient;
import tech.pegasys.teku.spec.executionengine.client.Web3JExecutionEngineClient;
import tech.pegasys.teku.spec.executionengine.client.schema.AssembleBlockRequest;
import tech.pegasys.teku.spec.executionengine.client.schema.ExecutionPayload;
import tech.pegasys.teku.spec.executionengine.client.schema.NewBlockResponse;
import tech.pegasys.teku.spec.executionengine.client.schema.Response;

public class ExecutionEngineService {

  private static final Logger LOG = LogManager.getLogger();

  private final ExecutionEngineClient executionEngineClient;
  private final ExecutionPayloadSchema executionPayloadSchema;

  public static ExecutionEngineService create(
      String eth1EngineEndpoint, ExecutionPayloadSchema executionPayloadSchema) {
    ExecutionEngineClient executionEngineClient =
        eth1EngineEndpoint != null
            ? new Web3JExecutionEngineClient(eth1EngineEndpoint)
            : ExecutionEngineClient.Stub;

    return new ExecutionEngineService(executionEngineClient, executionPayloadSchema);
  }

  public static ExecutionEngineService createStub(ExecutionPayloadSchema executionPayloadSchema) {
    return new ExecutionEngineService(ExecutionEngineClient.Stub, executionPayloadSchema);
  }

  public ExecutionEngineService(
      ExecutionEngineClient executionEngineClient, ExecutionPayloadSchema executionPayloadSchema) {
    this.executionEngineClient = executionEngineClient;
    this.executionPayloadSchema = executionPayloadSchema;
  }

  /**
   * Requests execution-engine to produce a block.
   *
   * @param parentHash the hash of execution block to produce atop of
   * @param timestamp the timestamp of the beginning of the slot
   * @return a response with execution payload
   */
  public tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload assembleBlock(
      Bytes32 parentHash, UInt64 timestamp) {

    AssembleBlockRequest request = new AssembleBlockRequest(parentHash, timestamp);

    try {
      Response<ExecutionPayload> response =
          executionEngineClient.consensusAssembleBlock(request).get();

      checkArgument(
          response.getPayload() != null,
          "Failed consensus_assembleBlock(parent_hash=%s, timestamp=%s), reason: %s",
          LogFormatter.formatHashRoot(parentHash),
          timestamp,
          response.getReason());

      LOG.info(
          ColorConsolePrinter.print(
              String.format(
                  "consensus_assembleBlock(parent_hash=%s, timestamp=%s) ~> %s",
                  LogFormatter.formatHashRoot(parentHash), timestamp, response.getPayload()),
              Color.CYAN));

      return response.getPayload().asInternalExecutionPayload(executionPayloadSchema);
    } catch (InterruptedException | ExecutionException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Requests execution-engine to process a block.
   *
   * @param executionPayload an executable payload
   * @return {@code true} if processing succeeded, {@code false} otherwise
   */
  public boolean newBlock(
      tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload executionPayload) {

    ExecutionPayload request = new ExecutionPayload(executionPayload);

    try {
      Response<NewBlockResponse> response = executionEngineClient.consensusNewBlock(request).get();

      checkArgument(
          response.getPayload() != null,
          "Failed consensus_newBlock(execution_payload=%s), reason: %s",
          request,
          response.getReason());

      LOG.info(
          ColorConsolePrinter.print(
              String.format(
                  "consensus_newBlock(execution_payload=%s) ~> %s", request, response.getPayload()),
              Color.CYAN));

      return response.getPayload().getValid();
    } catch (InterruptedException | ExecutionException e) {
      throw new IllegalStateException(e);
    }
  }
}
