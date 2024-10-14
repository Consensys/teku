package tech.pegasys.teku.spec.datastructures.builder.versions.electra;

import java.util.Optional;
import tech.pegasys.teku.spec.datastructures.builder.versions.deneb.BuilderBidDeneb;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionRequests;

public interface BuilderBidElectra extends BuilderBidDeneb {

  ExecutionRequests getExecutionRequests();

  @Override
  default Optional<ExecutionRequests> getOptionalExecutionRequests() {
    return Optional.of(getExecutionRequests());
  }
}
