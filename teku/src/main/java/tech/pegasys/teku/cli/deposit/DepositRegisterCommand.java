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
import java.io.File;
import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;
import tech.pegasys.signers.bls.keystore.KeyStore;
import tech.pegasys.signers.bls.keystore.KeyStoreLoader;
import tech.pegasys.signers.bls.keystore.KeyStoreValidationException;
import tech.pegasys.signers.bls.keystore.model.KeyStoreData;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSecretKey;
import tech.pegasys.teku.util.cli.PicoCliVersionProvider;

@Command(
    name = "register",
    description =
        "Register a validator from existing keys by sending a deposit transaction to an Ethereum 1 node",
    mixinStandardHelpOptions = true,
    showDefaultValues = true,
    abbreviateSynopsis = true,
    versionProvider = PicoCliVersionProvider.class,
    synopsisHeading = "%n",
    descriptionHeading = "%nDescription:%n%n",
    optionListHeading = "%nOptions:%n",
    footerHeading = "%n",
    footer = "Teku is licensed under the Apache License 2.0")
public class DepositRegisterCommand implements Runnable {
  private final Consumer<Integer> shutdownFunction;
  private final Function<String, String> envSupplier;
  @Spec private CommandSpec spec;
  @Mixin private RegisterParams registerParams;

  @ArgGroup(exclusive = true, multiplicity = "1")
  private ValidatorKeyOptions validatorKeyOptions;

  @Option(
      names = {"--withdrawal-public-key"},
      paramLabel = "<PUBLIC_KEY>",
      required = true,
      description = "Public withdrawal key for the validator")
  private String withdrawalKey;

  @Option(
      names = {"--Xconfirm-enabled"},
      arity = "1",
      defaultValue = "true",
      hidden = true)
  private boolean displayConfirmation = true;

  DepositRegisterCommand() {
    // required because web3j use non-daemon threads which halts the program
    this.shutdownFunction = System::exit;
    this.envSupplier = System::getenv;
  }

  @VisibleForTesting
  DepositRegisterCommand(
      final Consumer<Integer> shutdownFunction,
      final Function<String, String> envSupplier,
      final CommandSpec spec,
      final RegisterParams registerParams,
      final ValidatorKeyOptions validatorKeyOptions,
      final String withdrawalKey) {
    this.shutdownFunction = shutdownFunction;
    this.envSupplier = envSupplier;
    this.spec = spec;
    this.registerParams = registerParams;
    this.validatorKeyOptions = validatorKeyOptions;
    this.withdrawalKey = withdrawalKey;
  }

  @Override
  public void run() {
    final BLSKeyPair validatorKey = getValidatorKey();

    try (final RegisterAction registerAction =
        registerParams.createRegisterAction(displayConfirmation)) {
      final BLSPublicKey withdrawalPublicKey =
          BLSPublicKey.fromBytesCompressed(Bytes.fromHexString(this.withdrawalKey));
      registerAction.displayConfirmation(1);
      registerAction.sendDeposit(validatorKey, withdrawalPublicKey).get();
    } catch (final Throwable t) {
      SUB_COMMAND_LOG.sendDepositFailure(t);
      shutdownFunction.accept(1);
    }
    shutdownFunction.accept(0);
  }

  private BLSKeyPair getValidatorKey() {
    if (validatorKeyOptions.validatorKey != null) {
      return privateKeyToKeyPair(validatorKeyOptions.validatorKey);
    }

    try {
      final String keystorePassword = readPassword();

      final KeyStoreData keyStoreData =
          KeyStoreLoader.loadFromFile(
              validatorKeyOptions.validatorKeyStoreOptions.validatorKeystoreFile.toPath());
      final Bytes privateKey = KeyStore.decrypt(keystorePassword, keyStoreData);
      return privateKeyToKeyPair(privateKey);
    } catch (final KeyStoreValidationException e) {
      throw new ParameterException(spec.commandLine(), e.getMessage());
    }
  }

  private String readPassword() {
    final String keystorePassword;
    if (validatorKeyOptions.validatorKeyStoreOptions.validatorPasswordOptions.getPasswordFile()
        != null) {
      keystorePassword =
          KeystorePasswordOptions.readFromFile(
              spec.commandLine(),
              validatorKeyOptions.validatorKeyStoreOptions.validatorPasswordOptions
                  .getPasswordFile());
    } else {
      keystorePassword =
          KeystorePasswordOptions.readFromEnvironmentVariable(
              spec.commandLine(),
              envSupplier,
              validatorKeyOptions.validatorKeyStoreOptions.validatorPasswordOptions
                  .getPasswordEnvironmentVariable());
    }
    return keystorePassword;
  }

  private BLSKeyPair privateKeyToKeyPair(final String validatorKey) {
    return privateKeyToKeyPair(Bytes.fromHexString(validatorKey));
  }

  private BLSKeyPair privateKeyToKeyPair(final Bytes validatorKey) {
    return new BLSKeyPair(BLSSecretKey.fromBytes(validatorKey));
  }

  static class ValidatorKeyOptions {
    @Option(
        names = {"--validator-private-key"},
        paramLabel = "<PRIVATE_KEY>",
        required = true,
        description = "Private signing key for the validator")
    String validatorKey;

    @ArgGroup(exclusive = false, multiplicity = "1")
    ValidatorKeyStoreOptions validatorKeyStoreOptions;
  }

  static class ValidatorKeyStoreOptions {
    @Option(
        names = {"--encrypted-keystore-validator-file"},
        paramLabel = "<FILE>",
        required = true,
        description =
            "Path to the keystore file containing encrypted signing key for the validator")
    File validatorKeystoreFile;

    @ArgGroup(multiplicity = "1")
    ValidatorPasswordOptions validatorPasswordOptions;
  }

  static class ValidatorPasswordOptions implements KeystorePasswordOptions {
    @Option(
        names = {"--encrypted-keystore-validator-password-file"},
        paramLabel = "<FILE>",
        required = true,
        description = "Read password from the file to decrypt the validator keystore")
    File validatorKeystorePasswordFile;

    @Option(
        names = {"--encrypted-keystore-validator-password-env"},
        paramLabel = "<ENV_VAR>",
        required = true,
        description = "Read password from environment variable to decrypt the validator keystore")
    String validatorKeystorePasswordEnv;

    @Override
    public File getPasswordFile() {
      return validatorKeystorePasswordFile;
    }

    @Override
    public String getPasswordEnvironmentVariable() {
      return validatorKeystorePasswordEnv;
    }
  }
}
