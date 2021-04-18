package tech.pegasys.teku.spec.logic.common.util;

import static com.google.common.base.Preconditions.checkNotNull;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSchema;
import tech.pegasys.teku.spec.executionengine.ExecutionEngineService;

public class ExecutionPayloadUtil {

  private ExecutionEngineService executionEngineService;
  private final ExecutionPayloadSchema executionPayloadSchema;

  public ExecutionPayloadUtil(
      ExecutionEngineService executionEngineService,
      ExecutionPayloadSchema executionPayloadSchema) {
    this.executionEngineService = executionEngineService;
    this.executionPayloadSchema = executionPayloadSchema;
  }

  public ExecutionPayloadUtil(ExecutionPayloadSchema executionPayloadSchema) {
    this.executionPayloadSchema = executionPayloadSchema;
  }

  public boolean verifyExecutionStateTransition(ExecutionPayload executionPayload) {
    checkNotNull(executionEngineService);
    return executionEngineService.newBlock(
        new tech.pegasys.teku.spec.executionengine.client.schema.ExecutionPayload(
            executionPayload));
  }

  public ExecutionPayload produceExecutionPayload(Bytes32 parentHash, UInt64 timestamp) {
    checkNotNull(executionEngineService);
    return executionEngineService
        .assembleBlock(parentHash, timestamp)
        .asInternalExecutionPayload(executionPayloadSchema);
  }

  public void setExecutionEngineService(ExecutionEngineService executionEngineService) {
    this.executionEngineService = executionEngineService;
  }
}
