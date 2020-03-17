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

package tech.pegasys.artemis.cli.deposit;

import static tech.pegasys.artemis.cli.deposit.CommonParams.sendDeposit;
import static tech.pegasys.teku.logging.StatusLogger.STATUS_LOG;

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
import tech.pegasys.artemis.bls.keystore.KeyStore;
import tech.pegasys.artemis.bls.keystore.KeyStoreLoader;
import tech.pegasys.artemis.bls.keystore.KeyStoreValidationException;
import tech.pegasys.artemis.bls.keystore.model.KeyStoreData;
import tech.pegasys.artemis.services.powchain.DepositTransactionSender;
import tech.pegasys.artemis.util.bls.BLSKeyPair;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.bls.BLSSecretKey;
import tech.pegasys.artemis.util.cli.VersionProvider;

@Command(
    name = "register",
    description =
        "Register a validator from existing keys by sending a deposit transaction to an Ethereum 1 node",
    mixinStandardHelpOptions = true,
    abbreviateSynopsis = true,
    versionProvider = VersionProvider.class,
    synopsisHeading = "%n",
    descriptionHeading = "%nDescription:%n%n",
    optionListHeading = "%nOptions:%n",
    footerHeading = "%n",
    footer = "Teku is licensed under the Apache License 2.0")
public class DepositRegisterCommand implements Runnable {
  private final Consumer<Integer> shutdownFunction;
  private final Function<String, String> envSupplier;
  @Spec private CommandSpec spec;
  @Mixin private CommonParams params;

  @ArgGroup(exclusive = true, multiplicity = "1")
  private ValidatorKeyOptions validatorKeyOptions;

  @Option(
      names = {"-w", "--withdrawal-key"},
      paramLabel = "<PUBLIC_KEY>",
      required = true,
      description = "Public withdrawal key for the validator")
  private String withdrawalKey;

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
      final CommonParams params,
      final ValidatorKeyOptions validatorKeyOptions,
      final String withdrawalKey) {
    this.shutdownFunction = shutdownFunction;
    this.envSupplier = envSupplier;
    this.spec = spec;
    this.params = params;
    this.validatorKeyOptions = validatorKeyOptions;
    this.withdrawalKey = withdrawalKey;
  }

  @Override
  public void run() {
    final BLSKeyPair validatorKey = getValidatorKey();

    final CommonParams _params = params; // making it effective final as it gets injected by PicoCLI
    try (_params) {
      final DepositTransactionSender sender = params.createTransactionSender();
      sendDeposit(
              sender,
              validatorKey,
              BLSPublicKey.fromBytesCompressed(Bytes.fromHexString(withdrawalKey)),
              params.getAmount())
          .get();
    } catch (final Throwable t) {
      STATUS_LOG.sendDepositFailure(t);
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
        names = {"-s", "--signing-key"},
        paramLabel = "<PRIVATE_KEY>",
        required = true,
        description = "Private signing key for the validator")
    String validatorKey;

    @ArgGroup(exclusive = false, multiplicity = "1")
    ValidatorKeyStoreOptions validatorKeyStoreOptions;
  }

  static class ValidatorKeyStoreOptions {
    @Option(
        names = {"--signing-keystore-file"},
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
        names = {"--signing-keystore-password-file"},
        paramLabel = "<FILE>",
        required = true,
        description = "Read password from the file to decrypt the validator keystore")
    File validatorKeystorePasswordFile;

    @Option(
        names = {"--signing-keystore-password-env"},
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
