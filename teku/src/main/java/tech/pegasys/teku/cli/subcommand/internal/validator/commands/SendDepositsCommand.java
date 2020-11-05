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
import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import picocli.CommandLine;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;
import tech.pegasys.signers.bls.keystore.KeyStore;
import tech.pegasys.signers.bls.keystore.KeyStoreLoader;
import tech.pegasys.signers.bls.keystore.KeyStoreValidationException;
import tech.pegasys.signers.bls.keystore.model.KeyStoreData;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSecretKey;
import tech.pegasys.teku.cli.converter.PicoCliVersionProvider;
import tech.pegasys.teku.cli.options.WithdrawalPublicKeyOptions;
import tech.pegasys.teku.cli.subcommand.internal.validator.options.DepositOptions;
import tech.pegasys.teku.cli.subcommand.internal.validator.options.KeystorePasswordOptions;
import tech.pegasys.teku.cli.subcommand.internal.validator.options.ValidatorKeyOptions;
import tech.pegasys.teku.cli.subcommand.internal.validator.options.VerbosityOptions;
import tech.pegasys.teku.cli.subcommand.internal.validator.tools.DepositSender;

@Command(
    name = "send-deposits",
    description =
        "[WARNING: NOT FOR PRODUCTION USE] Register a validator from existing keys by sending a deposit transaction to an Ethereum 1 node",
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
public class SendDepositsCommand implements Runnable {
  private final Consumer<Integer> shutdownFunction;
  private final Function<String, String> envSupplier;
  @Spec private CommandSpec spec;
  @Mixin private DepositOptions depositOptions;

  @ArgGroup(exclusive = true, multiplicity = "1")
  private WithdrawalPublicKeyOptions withdrawalKeyOptions;

  @ArgGroup(exclusive = true, multiplicity = "1")
  private ValidatorKeyOptions validatorKeyOptions;

  @Mixin private VerbosityOptions verbosityOptions;

  SendDepositsCommand() {
    // required because web3j use non-daemon threads which halts the program
    this.shutdownFunction = System::exit;
    this.envSupplier = System::getenv;
  }

  @VisibleForTesting
  SendDepositsCommand(
      final Consumer<Integer> shutdownFunction,
      final Function<String, String> envSupplier,
      final CommandSpec spec,
      final DepositOptions depositOptions,
      final ValidatorKeyOptions validatorKeyOptions,
      final WithdrawalPublicKeyOptions withdrawalPublicKeyOptions) {
    this.shutdownFunction = shutdownFunction;
    this.envSupplier = envSupplier;
    this.spec = spec;
    this.depositOptions = depositOptions;
    this.validatorKeyOptions = validatorKeyOptions;
    this.withdrawalKeyOptions = withdrawalPublicKeyOptions;
    this.verbosityOptions = new VerbosityOptions(true);
  }

  public BLSPublicKey getWithdrawalPublicKey() {
    if (withdrawalKeyOptions.getWithdrawalKey() != null) {
      return BLSPublicKey.fromBytesCompressed(
          Bytes48.fromHexString(withdrawalKeyOptions.getWithdrawalKey()));
    } else if (withdrawalKeyOptions.getWithdrawalKeystoreFile() != null) {
      return getWithdrawalKeyFromKeystore();
    } else {
      // not meant to happen
      throw new IllegalStateException("Withdrawal Key Options are not initialized");
    }
  }

  private BLSPublicKey getWithdrawalKeyFromKeystore() {
    try {
      final KeyStoreData keyStoreData =
          KeyStoreLoader.loadFromFile(withdrawalKeyOptions.getWithdrawalKeystoreFile().toPath());
      return BLSPublicKey.fromBytesCompressed(Bytes48.wrap(keyStoreData.getPubkey()));
    } catch (final KeyStoreValidationException e) {
      throw new CommandLine.ParameterException(
          spec.commandLine(),
          "Error: Unable to get withdrawal key from keystore: " + e.getMessage(),
          e);
    }
  }

  @Override
  public void run() {
    SUB_COMMAND_LOG.commandIsNotSafeForProduction();
    final BLSKeyPair validatorKey = getValidatorKey();

    try (final DepositSender depositSender =
        depositOptions.createDepositSender(verbosityOptions.isVerboseOutputEnabled())) {
      depositSender.displayConfirmation(1);
      depositSender.sendDeposit(validatorKey, getWithdrawalPublicKey()).get();
    } catch (final Throwable t) {
      SUB_COMMAND_LOG.sendDepositFailure(t);
      shutdownFunction.accept(1);
    }
    shutdownFunction.accept(0);
  }

  private BLSKeyPair getValidatorKey() {
    if (validatorKeyOptions.getValidatorKey() != null) {
      return privateKeyToKeyPair(validatorKeyOptions.getValidatorKey());
    }

    try {
      final String keystorePassword = readPassword();

      final KeyStoreData keyStoreData =
          KeyStoreLoader.loadFromFile(
              validatorKeyOptions
                  .getValidatorKeyStoreOptions()
                  .getValidatorKeystoreFile()
                  .toPath());
      final Bytes privateKey = KeyStore.decrypt(keystorePassword, keyStoreData);
      return privateKeyToKeyPair(Bytes32.wrap(privateKey));
    } catch (final KeyStoreValidationException e) {
      throw new ParameterException(spec.commandLine(), e.getMessage());
    }
  }

  private String readPassword() {
    final String keystorePassword;
    if (validatorKeyOptions
            .getValidatorKeyStoreOptions()
            .getValidatorPasswordOptions()
            .getPasswordFile()
        != null) {
      keystorePassword =
          KeystorePasswordOptions.readFromFile(
              spec.commandLine(),
              validatorKeyOptions
                  .getValidatorKeyStoreOptions()
                  .getValidatorPasswordOptions()
                  .getPasswordFile());
    } else {
      keystorePassword =
          KeystorePasswordOptions.readFromEnvironmentVariable(
              spec.commandLine(),
              envSupplier,
              validatorKeyOptions
                  .getValidatorKeyStoreOptions()
                  .getValidatorPasswordOptions()
                  .getPasswordEnvironmentVariable());
    }
    return keystorePassword;
  }

  private BLSKeyPair privateKeyToKeyPair(final String validatorKey) {
    return privateKeyToKeyPair(Bytes32.fromHexString(validatorKey));
  }

  private BLSKeyPair privateKeyToKeyPair(final Bytes32 validatorKey) {
    return new BLSKeyPair(BLSSecretKey.fromBytes(validatorKey));
  }
}
