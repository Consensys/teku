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
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import tech.pegasys.signers.bls.keystore.KeyStore;
import tech.pegasys.signers.bls.keystore.KeyStoreLoader;
import tech.pegasys.teku.cli.deposit.GenerateAction.ValidatorPasswordOptions;
import tech.pegasys.teku.cli.deposit.GenerateAction.WithdrawalPasswordOptions;

class GenerateActionTest {

  private static final int VALIDATORS_COUNT = 2;
  private static final String EXPECTED_PASSWORD = "testpassword";
  private static final String EXPECTED_ENV_VARIABLE = "TEST_ENV";
  private static final Function<String, String> envSupplier =
      s -> EXPECTED_ENV_VARIABLE.equals(s) ? EXPECTED_PASSWORD : null;
  private static final boolean ENCRYPTED_KEYSTORE_ENABLED = true;
  private static final boolean DISPLAY_CONFIRMATION_ENABLED = true;
  private ConsoleAdapter consoleAdapter;
  private CommandSpec commandSpec;

  @BeforeEach
  void setUp() {
    consoleAdapter = mock(ConsoleAdapter.class);
    commandSpec = mock(CommandSpec.class);
    final CommandLine commandLine = mock(CommandLine.class);
    final RegisterAction registerAction = mock(RegisterAction.class);

    when(commandSpec.commandLine()).thenReturn(commandLine);
    when(consoleAdapter.isConsoleAvailable()).thenReturn(true);
    when(consoleAdapter.readPassword(anyString(), any()))
        .thenReturn(EXPECTED_PASSWORD.toCharArray());
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

    final GenerateAction generateAction =
        new GenerateAction(
            VALIDATORS_COUNT,
            outputPath.toString(),
            ENCRYPTED_KEYSTORE_ENABLED,
            validatorPasswordOptions,
            withdrawalPasswordOptions,
            DISPLAY_CONFIRMATION_ENABLED,
            consoleAdapter,
            commandSpec,
            envSupplier);
    generateAction.generateKeys();

    // assert that files exist: 2 per validator
    final File[] keystoreFiles = outputPath.toFile().listFiles();
    assertThat(keystoreFiles).hasSize(VALIDATORS_COUNT * 2);
    // all files should have the same password
    assertKeyStoreFilesExistAndAreEncryptedWithPassword(outputPath);

    // select only withdrawal files
    FilenameFilter withdrawalFilter =
        new FilenameFilter() {
          @Override
          public boolean accept(File dir, String name) {
            String lowercaseName = name.toLowerCase();
            if (lowercaseName.contains("withdrawal")) {
              return true;
            } else {
              return false;
            }
          }
        };

    // assert that files exist: 1 withdrawal file per validator
    final File[] withdrawalFiles = outputPath.toFile().listFiles(withdrawalFilter);
    assertThat(withdrawalFiles).hasSize(VALIDATORS_COUNT);
    Arrays.stream(withdrawalFiles).forEach(file -> assertThat(file).isFile());

    // select only validator files
    FilenameFilter validatorFilter =
        new FilenameFilter() {
          @Override
          public boolean accept(File dir, String name) {
            String lowercaseName = name.toLowerCase();
            if (lowercaseName.contains("validator")) {
              return true;
            } else {
              return false;
            }
          }
        };

    // assert that files exist: 1 validator file per validator
    final File[] validatorFiles = outputPath.toFile().listFiles(validatorFilter);
    assertThat(validatorFiles).hasSize(VALIDATORS_COUNT);
    Arrays.stream(validatorFiles).forEach(file -> assertThat(file).isFile());
  }

  private void assertKeyStoreFilesExistAndAreEncryptedWithPassword(final Path parentDir) {
    final File[] keyStoreFiles = parentDir.toFile().listFiles();
    for (File file : keyStoreFiles) {
      assertThat(
              KeyStore.validatePassword(
                  EXPECTED_PASSWORD, KeyStoreLoader.loadFromFile(file.toPath())))
          .isTrue();
    }
  }
}
