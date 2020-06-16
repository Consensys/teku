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

package tech.pegasys.teku.cli.deposit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.cli.deposit.GenerateAction.ValidatorKeys;
import tech.pegasys.teku.util.async.SafeFuture;

class DepositGenerateAndRegisterCommandTest {
  private static final Consumer<Integer> shutdownFunction = status -> {};

  private GenerateParams generateParams;
  private GenerateAction generateAction;
  private RegisterParams registerParams;
  private RegisterAction registerAction;

  @BeforeEach
  void setUp() {
    generateParams = mock(GenerateParams.class);
    registerParams = mock(RegisterParams.class);
    generateAction = mock(GenerateAction.class);
    registerAction = mock(RegisterAction.class);
    when(registerParams.createRegisterAction(anyBoolean())).thenReturn(registerAction);
    when(generateParams.createGenerateAction(anyBoolean())).thenReturn(generateAction);
  }

  @Test
  public void generatesAKeysAndRegistersIt() {
    final List<ValidatorKeys> validatorKeys = createValidatorKeys(1);

    when(generateParams.getValidatorCount()).thenReturn(1);
    when(generateAction.generateKeysStream()).thenReturn(validatorKeys.stream());
    when(registerAction.sendDeposit(any(), any()))
        .thenReturn(SafeFuture.completedFuture(new TransactionReceipt()));

    final DepositGenerateAndRegisterCommand depositGenerateAndRegisterCommand =
        new DepositGenerateAndRegisterCommand(
            shutdownFunction, registerParams, generateParams, true);

    depositGenerateAndRegisterCommand.run();

    final ValidatorKeys keys = validatorKeys.get(0);
    verify(generateAction).generateKeysStream();
    verify(registerAction)
        .sendDeposit(keys.getValidatorKey(), keys.getWithdrawalKey().getPublicKey());
    verify(registerAction).close();
  }

  @Test
  public void generatesMultipleKeysAndRegistersThem() {
    final int noOfKeysToCreate = 3;
    final List<ValidatorKeys> validatorKeys = createValidatorKeys(noOfKeysToCreate);

    when(generateParams.getValidatorCount()).thenReturn(noOfKeysToCreate);
    when(generateAction.generateKeysStream()).thenReturn(validatorKeys.stream());
    when(registerAction.sendDeposit(any(), any()))
        .thenReturn(SafeFuture.completedFuture(new TransactionReceipt()));

    final DepositGenerateAndRegisterCommand depositGenerateAndRegisterCommand =
        new DepositGenerateAndRegisterCommand(
            shutdownFunction, registerParams, generateParams, true);

    depositGenerateAndRegisterCommand.run();

    verify(generateAction).generateKeysStream();
    for (int i = 0; i < noOfKeysToCreate; i++) {
      verify(registerAction)
          .sendDeposit(
              validatorKeys.get(i).getValidatorKey(),
              validatorKeys.get(i).getWithdrawalKey().getPublicKey());
    }
    verify(registerAction).close();
  }

  @Test
  public void generatesAndRegistersWithoutDisplayConfirmation() {
    final DepositGenerateAndRegisterCommand depositGenerateAndRegisterCommand =
        new DepositGenerateAndRegisterCommand(
            shutdownFunction, registerParams, generateParams, false);

    depositGenerateAndRegisterCommand.run();
    verify(generateParams).createGenerateAction(false);
    verify(registerParams).createRegisterAction(false);
    verify(generateAction).generateKeysStream();
  }

  @Test
  public void generatesAndRegistersWithDisplayConfirmation() {
    final DepositGenerateAndRegisterCommand depositGenerateAndRegisterCommand =
        new DepositGenerateAndRegisterCommand(
            shutdownFunction, registerParams, generateParams, true);

    depositGenerateAndRegisterCommand.run();
    verify(generateParams).createGenerateAction(true);
    verify(registerParams).createRegisterAction(true);
    verify(generateAction).generateKeysStream();
  }

  private List<ValidatorKeys> createValidatorKeys(final int noOfKeysToCreate) {
    final List<ValidatorKeys> validatorKeys = new ArrayList<>();
    for (int i = 1; i <= noOfKeysToCreate * 2; i += 2) {
      final BLSKeyPair validatorKey = BLSKeyPair.random(i);
      final BLSKeyPair withdrawalKey = BLSKeyPair.random(i + 1);
      validatorKeys.add(new ValidatorKeys(validatorKey, withdrawalKey));
    }
    return validatorKeys;
  }
}
