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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.util.async.SafeFuture.completedFuture;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import tech.pegasys.signers.bls.keystore.KeyStore;
import tech.pegasys.signers.bls.keystore.KeyStoreLoader;
import tech.pegasys.teku.cli.deposit.DepositGenerateCommand.ValidatorPasswordOptions;
import tech.pegasys.teku.cli.deposit.DepositGenerateCommand.WithdrawalPasswordOptions;

class DepositGenerateCommandTest {
  private static final int VALIDATORS_COUNT = 2;
  private static final String EXPECTED_PASSWORD = "testpassword";
  private static final String EXPECTED_ENV_VARIABLE = "TEST_ENV";
  private static final Function<String, String> envSupplier =
      s -> EXPECTED_ENV_VARIABLE.equals(s) ? EXPECTED_PASSWORD : null;
  private static final boolean ENCRYPTED_KEYSTORE_ENABLED = true;
  private static final Consumer<Integer> shutdownFunction = status -> {};
  private ConsoleAdapter consoleAdapter;
  private CommonParams commonParams;
  private CommandSpec commandSpec;

  @BeforeEach
  void setUp() {
    consoleAdapter = mock(ConsoleAdapter.class);
    commonParams = mock(CommonParams.class);
    commandSpec = mock(CommandSpec.class);
    final CommandLine commandLine = mock(CommandLine.class);
    final RegisterAction registerAction = mock(RegisterAction.class);

    when(commandSpec.commandLine()).thenReturn(commandLine);
    when(consoleAdapter.isConsoleAvailable()).thenReturn(true);
    when(consoleAdapter.readPassword(anyString(), any()))
        .thenReturn(EXPECTED_PASSWORD.toCharArray());
    when(commonParams.createRegisterAction()).thenReturn(registerAction);
    when(registerAction.sendDeposit(any(), any())).thenReturn(completedFuture(null));
  }

  @Test
  void encryptedKeystoresAreCreatedWithInteractivePassword(@TempDir final Path tempDir)
      throws IOException {
    final Path outputPath = Files.createTempDirectory(tempDir, "keystores");
    assertEncryptedKeystoresAreCreated(outputPath, null, null);
  }

  @Test
  void emptyInteractivePasswordRaisesError(@TempDir final Path tempDir) throws IOException {
    when(consoleAdapter.readPassword(anyString(), any())).thenReturn(null);
    final Path outputPath = Files.createTempDirectory(tempDir, "keystores");
    assertThatExceptionOfType(CommandLine.ParameterException.class)
        .isThrownBy(() -> assertEncryptedKeystoresAreCreated(outputPath, null, null))
        .withMessage("Error: Password is blank");
  }

  @Test
  void mismatchedValidatorInteractivePasswordRaisesError(@TempDir final Path tempDir)
      throws IOException {
    when(consoleAdapter.readPassword(anyString(), any()))
        .thenReturn(EXPECTED_PASSWORD.toCharArray())
        .thenReturn("mismatched".toCharArray());
    final Path outputPath = Files.createTempDirectory(tempDir, "keystores");
    assertThatExceptionOfType(CommandLine.ParameterException.class)
        .isThrownBy(() -> assertEncryptedKeystoresAreCreated(outputPath, null, null))
        .withMessage("Error: Password mismatched");
  }

  @Test
  void encryptedKeystoresAreCreatedWithPasswordFromEnvironmentVariable(@TempDir final Path tempDir)
      throws IOException {
    final Path outputPath = Files.createTempDirectory(tempDir, "keystores");
    final ValidatorPasswordOptions validatorPasswordOptions = new ValidatorPasswordOptions();
    validatorPasswordOptions.validatorPasswordEnv = EXPECTED_ENV_VARIABLE;
    final WithdrawalPasswordOptions withdrawalPasswordOptions = new WithdrawalPasswordOptions();
    withdrawalPasswordOptions.withdrawalPasswordEnv = EXPECTED_ENV_VARIABLE;

    assertEncryptedKeystoresAreCreated(
        outputPath, validatorPasswordOptions, withdrawalPasswordOptions);
  }

  @Test
  void invalidEnvironmentVariableRaisesException(@TempDir final Path tempDir) throws IOException {
    final Path outputPath = Files.createTempDirectory(tempDir, "keystores");
    final ValidatorPasswordOptions validatorPasswordOptions = new ValidatorPasswordOptions();
    validatorPasswordOptions.validatorPasswordEnv = "INVALID_ENV";
    final WithdrawalPasswordOptions withdrawalPasswordOptions = new WithdrawalPasswordOptions();
    withdrawalPasswordOptions.withdrawalPasswordEnv = "INVALID_ENV";

    assertThatExceptionOfType(CommandLine.ParameterException.class)
        .isThrownBy(
            () ->
                assertEncryptedKeystoresAreCreated(
                    outputPath, validatorPasswordOptions, withdrawalPasswordOptions))
        .withMessage("Error: Password cannot be read from environment variable: INVALID_ENV");
  }

  @Test
  void encryptedKeystoresAreCreatedWithPasswordFromFile(@TempDir final Path tempDir)
      throws IOException {
    final Path outputPath = Files.createTempDirectory(tempDir, "keystores");
    final Path passwordFile = Files.writeString(tempDir.resolve("password.txt"), EXPECTED_PASSWORD);

    final ValidatorPasswordOptions validatorPasswordOptions = new ValidatorPasswordOptions();
    validatorPasswordOptions.validatorPasswordFile = passwordFile.toFile();
    final WithdrawalPasswordOptions withdrawalPasswordOptions = new WithdrawalPasswordOptions();
    withdrawalPasswordOptions.withdrawalPasswordFile = passwordFile.toFile();

    assertEncryptedKeystoresAreCreated(
        outputPath, validatorPasswordOptions, withdrawalPasswordOptions);
  }

  @Test
  void invalidPasswordFileRaisesException(@TempDir final Path tempDir) throws IOException {
    final Path outputPath = Files.createTempDirectory(tempDir, "keystores");
    final ValidatorPasswordOptions validatorPasswordOptions = new ValidatorPasswordOptions();
    final File passwordFile = tempDir.resolve("nonexistent.txt").toFile();
    validatorPasswordOptions.validatorPasswordFile = passwordFile;
    final WithdrawalPasswordOptions withdrawalPasswordOptions = new WithdrawalPasswordOptions();
    withdrawalPasswordOptions.withdrawalPasswordFile = passwordFile;

    assertThatExceptionOfType(CommandLine.ParameterException.class)
        .isThrownBy(
            () ->
                assertEncryptedKeystoresAreCreated(
                    outputPath, validatorPasswordOptions, withdrawalPasswordOptions))
        .withMessage("Error: File not found: " + passwordFile);
  }

  @Test
  void emptyPasswordFileRaisesException(@TempDir final Path tempDir) throws IOException {
    final Path outputPath = Files.createTempDirectory(tempDir, "keystores");
    final ValidatorPasswordOptions validatorPasswordOptions = new ValidatorPasswordOptions();
    final File passwordFile = Files.writeString(outputPath.resolve("password.txt"), "").toFile();
    validatorPasswordOptions.validatorPasswordFile = passwordFile;
    final WithdrawalPasswordOptions withdrawalPasswordOptions = new WithdrawalPasswordOptions();
    withdrawalPasswordOptions.withdrawalPasswordFile = passwordFile;

    assertThatExceptionOfType(CommandLine.ParameterException.class)
        .isThrownBy(
            () ->
                assertEncryptedKeystoresAreCreated(
                    outputPath, validatorPasswordOptions, withdrawalPasswordOptions))
        .withMessage("Error: Empty password from file: " + passwordFile);
  }

  private void assertEncryptedKeystoresAreCreated(
      final Path outputPath,
      final ValidatorPasswordOptions validatorPasswordOptions,
      final WithdrawalPasswordOptions withdrawalPasswordOptions) {
    final DepositGenerateCommand depositGenerateCommand =
        new DepositGenerateCommand(
            shutdownFunction,
            consoleAdapter,
            envSupplier,
            commandSpec,
            commonParams,
            VALIDATORS_COUNT,
            outputPath.toString(),
            ENCRYPTED_KEYSTORE_ENABLED,
            validatorPasswordOptions,
            withdrawalPasswordOptions);
    depositGenerateCommand.run();

    // assert that sub directories exist
    final File[] subDirectories = outputPath.toFile().listFiles();
    assertThat(subDirectories).hasSize(VALIDATORS_COUNT);
    Arrays.stream(subDirectories).forEach(file -> assertThat(file).isDirectory());

    for (final File subDirectory : subDirectories) {
      assertKeyStoreFilesExist(subDirectory.toPath());
    }
  }

  private void assertKeyStoreFilesExist(final Path parentDir) {
    final File[] keyStoreFiles = parentDir.toFile().listFiles();
    assertThat(keyStoreFiles).hasSize(2);

    assertThat(
            KeyStore.validatePassword(
                EXPECTED_PASSWORD, KeyStoreLoader.loadFromFile(keyStoreFiles[0].toPath())))
        .isTrue();
    assertThat(
            KeyStore.validatePassword(
                EXPECTED_PASSWORD, KeyStoreLoader.loadFromFile(keyStoreFiles[1].toPath())))
        .isTrue();
  }
}
