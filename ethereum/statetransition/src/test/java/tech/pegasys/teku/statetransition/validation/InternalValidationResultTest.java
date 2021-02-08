package tech.pegasys.teku.statetransition.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

public class InternalValidationResultTest {

  @Test
  void shouldPrintAcceptToString() {
    assertThat(InternalValidationResult.create(ValidationResultCode.ACCEPT).toString()).isEqualTo("InternalValidationResult{validationResultCode=ACCEPT}");
  }
  @Test
  void shouldPrintRejectToString() {
    assertThat(InternalValidationResult.create(ValidationResultCode.REJECT, "good reason").toString())
        .isEqualTo("InternalValidationResult{validationResultCode=REJECT, description=good reason}");
  }
}
