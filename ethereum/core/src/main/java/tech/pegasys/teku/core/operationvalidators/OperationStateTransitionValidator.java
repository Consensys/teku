package tech.pegasys.teku.core.operationvalidators;

import tech.pegasys.teku.datastructures.operations.BlockOperation;
import tech.pegasys.teku.datastructures.state.BeaconState;

import java.util.Optional;

public interface OperationStateTransitionValidator<T> {

  Optional<OperationInvalidReason> validate(final BeaconState state,
                                            final T operation);
}
