package tech.pegasys.teku.test.acceptance.dsl.tools.deposits;

import tech.pegasys.teku.bls.BLSKeyPair;

public class ValidatorKeys {
  private final BLSKeyPair validatorKey;
  private final BLSKeyPair withdrawalKey;

  public ValidatorKeys(final BLSKeyPair validatorKey, final BLSKeyPair withdrawalKey) {
    this.validatorKey = validatorKey;
    this.withdrawalKey = withdrawalKey;
  }

  public BLSKeyPair getValidatorKey() {
    return validatorKey;
  }

  public BLSKeyPair getWithdrawalKey() {
    return withdrawalKey;
  }
}
