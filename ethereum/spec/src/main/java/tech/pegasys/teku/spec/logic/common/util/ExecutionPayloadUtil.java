package tech.pegasys.teku.spec.logic.common.util;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsMerge;

public class ExecutionPayloadUtil {

  private final SchemaDefinitionsMerge schemaDefinitions;

  public ExecutionPayloadUtil(SchemaDefinitionsMerge schemaDefinitions) {
    this.schemaDefinitions = schemaDefinitions;
  }

  public boolean verifyExecutionStateTransition(ExecutionPayload executionPayload) {
    return true;
  }

  public ExecutionPayload produceExecutionPayload(Bytes32 parentHash, UInt64 timestamp) {
    return schemaDefinitions.getExecutionPayloadSchema().createEmpty();
  }
}
