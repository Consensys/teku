package net.consensys.errorpronechecks.testdata;

import java.util.Optional;
import javax.annotation.Nullable;


public class DoNotReturnNullOptionalsNegativeCases {

  public interface allInterfacesAreValid {
    public Optional<Long> ExpectToBeOverridden();
  }

  public DoNotReturnNullOptionalsNegativeCases() {}

  public Optional<Long> doesNotReturnNull() {
    return Optional.of(3L);
  }

  @Nullable
  public Optional<Long> returnsNullButAnnotatedWithNullable() {
    return Optional.empty();
  }
}
