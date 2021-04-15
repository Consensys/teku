package tech.pegasys.teku.spec.logic.common.util;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.spec.executionengine.ExecutionEngineService;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;

public class ExecutionPayloadUtil {

  private final ExecutionEngineService executionEngineService;

  public ExecutionPayloadUtil(ExecutionEngineService executionEngineService) {
    this.executionEngineService = executionEngineService;
  }

  public boolean verifyExecutionStateTransition(ExecutionPayload executionPayload) {
    return executionEngineService.newBlock(executionPayload);
  }

  public ExecutionPayload produceExecutionPayload(Bytes32 parentHash, UInt64 timestamp) {
    return executionEngineService.assembleBlock(parentHash, timestamp);
  }
}
