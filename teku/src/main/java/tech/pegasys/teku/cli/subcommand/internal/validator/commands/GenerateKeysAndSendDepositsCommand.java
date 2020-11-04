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

import static tech.pegasys.teku.infrastructure.logging.SubCommandLogger.SUB_COMMAND_LOG;

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
import tech.pegasys.teku.cli.converter.PicoCliVersionProvider;
import tech.pegasys.teku.cli.subcommand.internal.validator.options.DepositOptions;
import tech.pegasys.teku.cli.subcommand.internal.validator.options.KeyGenerationOptions;
import tech.pegasys.teku.cli.subcommand.internal.validator.options.VerbosityOptions;
import tech.pegasys.teku.cli.subcommand.internal.validator.tools.DepositSender;
import tech.pegasys.teku.cli.subcommand.internal.validator.tools.KeyGenerator;
import tech.pegasys.teku.cli.subcommand.internal.validator.tools.ValidatorKeys;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

@Command(
    name = "generate-keys-and-send-deposits",
    description =
        "[WARNING: NOT FOR PRODUCTION USE] Register validators by generating new keys and sending deposit transactions to an Ethereum 1 node",
    hidden = true,
    mixinStandardHelpOptions = true,
    showDefaultValues = true,
    abbreviateSynopsis = true,
    versionProvider = PicoCliVersionProvider.class,
    synopsisHeading = "%n",
    descriptionHeading = "%nDescription:%n%n",
    optionListHeading = "%nOptions:%n",
    footerHeading = "%n",
    footer = "Teku is licensed under the Apache License 2.0")
public class GenerateKeysAndSendDepositsCommand implements Runnable {
  private final Consumer<Integer> shutdownFunction;

  @Mixin private DepositOptions depositOptions;
  @Mixin private KeyGenerationOptions keyGenerationOptions;
  @Mixin private VerbosityOptions verbosityOptions;

  public GenerateKeysAndSendDepositsCommand() {
    this.shutdownFunction =
        System::exit; // required because web3j use non-daemon threads which halts the program
  }

  @VisibleForTesting
  GenerateKeysAndSendDepositsCommand(
      final Consumer<Integer> shutdownFunction,
      final DepositOptions depositOptions,
      final KeyGenerationOptions keyGenerationOptions,
      final VerbosityOptions verbosityOptions) {
    this.shutdownFunction = shutdownFunction;
    this.depositOptions = depositOptions;
    this.keyGenerationOptions = keyGenerationOptions;
    this.verbosityOptions = verbosityOptions;
  }

  @Override
  public void run() {
    SUB_COMMAND_LOG.commandIsNotSafeForProduction();
    final KeyGenerator keyGenerator =
        keyGenerationOptions.createKeyGenerator(verbosityOptions.isVerboseOutputEnabled());

    try (final DepositSender depositSender =
        depositOptions.createDepositSender(verbosityOptions.isVerboseOutputEnabled())) {
      depositSender.displayConfirmation(keyGenerationOptions.getValidatorCount());
      final List<SafeFuture<TransactionReceipt>> transactionReceipts =
          keyGenerator
              .generateKeysStream()
              .map(registerValidator(depositSender))
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
      final DepositSender depositSender) {
    return validatorKey ->
        depositSender.sendDeposit(
            validatorKey.getValidatorKey(), validatorKey.getWithdrawalKey().getPublicKey());
  }
}
