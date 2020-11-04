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

package tech.pegasys.teku.cli.subcommand.internal.validator.commands;

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
import tech.pegasys.teku.cli.subcommand.internal.validator.options.DepositOptions;
import tech.pegasys.teku.cli.subcommand.internal.validator.options.KeyGenerationOptions;
import tech.pegasys.teku.cli.subcommand.internal.validator.options.VerbosityOptions;
import tech.pegasys.teku.cli.subcommand.internal.validator.tools.DepositSender;
import tech.pegasys.teku.cli.subcommand.internal.validator.tools.KeyGenerator;
import tech.pegasys.teku.cli.subcommand.internal.validator.tools.ValidatorKeys;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

class GenerateKeysAndSendDepositsCommandTest {
  private static final Consumer<Integer> shutdownFunction = status -> {};

  private KeyGenerationOptions keyGenerationOptions;
  private KeyGenerator keyGenerator;
  private DepositOptions depositOptions;
  private DepositSender depositSender;

  @BeforeEach
  void setUp() {
    keyGenerationOptions = mock(KeyGenerationOptions.class);
    depositOptions = mock(DepositOptions.class);
    keyGenerator = mock(KeyGenerator.class);
    depositSender = mock(DepositSender.class);
    when(depositOptions.createDepositSender(anyBoolean())).thenReturn(depositSender);
    when(keyGenerationOptions.createKeyGenerator(anyBoolean())).thenReturn(keyGenerator);
  }

  @Test
  public void generatesAKeysAndRegistersIt() {
    final List<ValidatorKeys> validatorKeys = createValidatorKeys(1);

    when(keyGenerationOptions.getValidatorCount()).thenReturn(1);
    when(keyGenerator.generateKeysStream()).thenReturn(validatorKeys.stream());
    when(depositSender.sendDeposit(any(), any()))
        .thenReturn(SafeFuture.completedFuture(new TransactionReceipt()));

    final GenerateKeysAndSendDepositsCommand generateKeysAndSendDepositsCommand =
        new GenerateKeysAndSendDepositsCommand(
            shutdownFunction, depositOptions, keyGenerationOptions, new VerbosityOptions(true));

    generateKeysAndSendDepositsCommand.run();

    final ValidatorKeys keys = validatorKeys.get(0);
    verify(keyGenerator).generateKeysStream();
    verify(depositSender)
        .sendDeposit(keys.getValidatorKey(), keys.getWithdrawalKey().getPublicKey());
    verify(depositSender).close();
  }

  @Test
  public void generatesMultipleKeysAndRegistersThem() {
    final int noOfKeysToCreate = 3;
    final List<ValidatorKeys> validatorKeys = createValidatorKeys(noOfKeysToCreate);

    when(keyGenerationOptions.getValidatorCount()).thenReturn(noOfKeysToCreate);
    when(keyGenerator.generateKeysStream()).thenReturn(validatorKeys.stream());
    when(depositSender.sendDeposit(any(), any()))
        .thenReturn(SafeFuture.completedFuture(new TransactionReceipt()));

    final GenerateKeysAndSendDepositsCommand generateKeysAndSendDepositsCommand =
        new GenerateKeysAndSendDepositsCommand(
            shutdownFunction, depositOptions, keyGenerationOptions, new VerbosityOptions(true));

    generateKeysAndSendDepositsCommand.run();

    verify(keyGenerator).generateKeysStream();
    for (int i = 0; i < noOfKeysToCreate; i++) {
      verify(depositSender)
          .sendDeposit(
              validatorKeys.get(i).getValidatorKey(),
              validatorKeys.get(i).getWithdrawalKey().getPublicKey());
    }
    verify(depositSender).close();
  }

  @Test
  public void generatesAndRegistersWithoutDisplayConfirmation() {
    final GenerateKeysAndSendDepositsCommand generateKeysAndSendDepositsCommand =
        new GenerateKeysAndSendDepositsCommand(
            shutdownFunction, depositOptions, keyGenerationOptions, new VerbosityOptions(false));

    generateKeysAndSendDepositsCommand.run();
    verify(keyGenerationOptions).createKeyGenerator(false);
    verify(depositOptions).createDepositSender(false);
    verify(keyGenerator).generateKeysStream();
  }

  @Test
  public void generatesAndRegistersWithDisplayConfirmation() {
    final GenerateKeysAndSendDepositsCommand generateKeysAndSendDepositsCommand =
        new GenerateKeysAndSendDepositsCommand(
            shutdownFunction, depositOptions, keyGenerationOptions, new VerbosityOptions(true));

    generateKeysAndSendDepositsCommand.run();
    verify(keyGenerationOptions).createKeyGenerator(true);
    verify(depositOptions).createDepositSender(true);
    verify(keyGenerator).generateKeysStream();
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
