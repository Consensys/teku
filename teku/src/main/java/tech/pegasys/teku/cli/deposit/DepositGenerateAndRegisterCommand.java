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

import static tech.pegasys.teku.logging.SubCommandLogger.SUB_COMMAND_LOG;

import com.google.common.annotations.VisibleForTesting;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import tech.pegasys.teku.cli.deposit.GenerateAction.ValidatorKeys;
import tech.pegasys.teku.util.async.SafeFuture;
import tech.pegasys.teku.util.cli.PicoCliVersionProvider;

@Command(
    name = "generate-and-register",
    description =
        "Register validators by generating new keys and sending deposit transactions to an Ethereum 1 node",
    mixinStandardHelpOptions = true,
    showDefaultValues = true,
    abbreviateSynopsis = true,
    versionProvider = PicoCliVersionProvider.class,
    synopsisHeading = "%n",
    descriptionHeading = "%nDescription:%n%n",
    optionListHeading = "%nOptions:%n",
    footerHeading = "%n",
    footer = "Teku is licensed under the Apache License 2.0")
public class DepositGenerateAndRegisterCommand implements Runnable {
  private final Consumer<Integer> shutdownFunction;

  @Mixin private RegisterParams registerParams;
  @Mixin private GenerateParams generateParams;

  @Option(
      names = {"--Xconfirm-enabled"},
      arity = "1",
      defaultValue = "true",
      hidden = true)
  private boolean displayConfirmation = true;

  public DepositGenerateAndRegisterCommand() {
    this.shutdownFunction =
        System::exit; // required because web3j use non-daemon threads which halts the program
  }

  @VisibleForTesting
  DepositGenerateAndRegisterCommand(
      final Consumer<Integer> shutdownFunction,
      final RegisterParams registerParams,
      final GenerateParams generateParams,
      final boolean displayConfirmation) {
    this.shutdownFunction = shutdownFunction;
    this.registerParams = registerParams;
    this.generateParams = generateParams;
    this.displayConfirmation = displayConfirmation;
  }

  @Override
  public void run() {
    final GenerateAction generateAction = generateParams.createGenerateAction(displayConfirmation);

    try (final RegisterAction registerAction =
        registerParams.createRegisterAction(displayConfirmation)) {
      registerAction.displayConfirmation(generateParams.getValidatorCount());
      final List<SafeFuture<TransactionReceipt>> transactionReceipts =
          generateAction
              .generateKeysStream()
              .map(registerValidator(registerAction))
              .collect(Collectors.toList());
      SafeFuture.allOf(transactionReceipts.toArray(SafeFuture[]::new)).get(2, TimeUnit.MINUTES);
    } catch (final Throwable t) {
      SUB_COMMAND_LOG.sendDepositFailure(t);
      shutdownFunction.accept(1);
    }
    shutdownFunction.accept(0);
  }

  @NotNull
  private Function<ValidatorKeys, SafeFuture<TransactionReceipt>> registerValidator(
      final RegisterAction registerAction) {
    return validatorKey ->
        registerAction.sendDeposit(
            validatorKey.getValidatorKey(), validatorKey.getWithdrawalKey().getPublicKey());
  }
}
