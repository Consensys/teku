package tech.pegasys.teku.core.operationvalidators;

import com.google.errorprone.annotations.CheckReturnValue;

import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

public interface OperationInvalidReason {
  String describe();

  @SafeVarargs
  static Optional<OperationInvalidReason> firstOf(
          final Supplier<Optional<OperationInvalidReason>>... checks) {
    return Stream.of(checks)
            .map(Supplier::get)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .findFirst();
  }

  @CheckReturnValue
  static Optional<OperationInvalidReason> check(
          final boolean isValid, final OperationInvalidReason check) {
    return !isValid ? Optional.of(check) : Optional.empty();
  }
}
