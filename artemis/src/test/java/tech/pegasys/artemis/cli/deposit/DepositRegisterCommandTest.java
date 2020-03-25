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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.artemis.util.async.SafeFuture.completedFuture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
import tech.pegasys.artemis.cli.deposit.DepositRegisterCommand.ValidatorKeyOptions;
import tech.pegasys.artemis.cli.deposit.DepositRegisterCommand.ValidatorKeyStoreOptions;
import tech.pegasys.artemis.cli.deposit.DepositRegisterCommand.ValidatorPasswordOptions;
import tech.pegasys.artemis.services.powchain.DepositTransactionSender;
import tech.pegasys.signers.bls.keystore.KeyStore;
import tech.pegasys.signers.bls.keystore.KeyStoreLoader;
import tech.pegasys.signers.bls.keystore.model.Cipher;
import tech.pegasys.signers.bls.keystore.model.KdfParam;
import tech.pegasys.signers.bls.keystore.model.KeyStoreData;
import tech.pegasys.signers.bls.keystore.model.SCryptParam;

class DepositRegisterCommandTest {
  private static final Consumer<Integer> shutdownFunction = status -> {};
  private static final String PASSWORD = "testpassword";
  private static final String EXPECTED_ENV_VARIABLE = "TEST_ENV";
  private static final Function<String, String> envSupplier =
      s -> EXPECTED_ENV_VARIABLE.equals(s) ? PASSWORD : null;
  private static final Bytes BLS_PRIVATE_KEY =
      Bytes48.fromHexStringLenient("19d6689c085ae165831e934ff763ae46a2a6c172b3f1b60a8ce26f");
  private static final Bytes32 SALT =
      Bytes32.fromHexString("d4e56740f876aef8c010b86a40d5f56745a118d0906a34e69aec8c0db1cb8fa3");
  private static final Bytes AES_IV_PARAM = Bytes.fromHexString("264daa3f303d7259501c93d997d84fe6");
  private static final Cipher CIPHER = new Cipher(AES_IV_PARAM);
  private static final KdfParam KDF_PARAM = new SCryptParam(32, 262144, 1, 8, SALT);
  private static final KeyStoreData VALIDATOR_KEYSTORE =
      KeyStore.encrypt(BLS_PRIVATE_KEY, Bytes32.random(), PASSWORD, "", KDF_PARAM, CIPHER);
  private CommonParams commonParams;
  private CommandLine.Model.CommandSpec commandSpec;
  private DepositTransactionSender depositTransactionSender;

  @BeforeEach
  void setUp() {
    commonParams = mock(CommonParams.class);
    commandSpec = mock(CommandLine.Model.CommandSpec.class);
    final CommandLine commandLine = mock(CommandLine.class);
    depositTransactionSender = mock(DepositTransactionSender.class);

    when(commandSpec.commandLine()).thenReturn(commandLine);
    when(commonParams.createTransactionSender()).thenReturn(depositTransactionSender);
    when(depositTransactionSender.sendDepositTransaction(any(), any(), any()))
        .thenReturn(completedFuture(null));
  }

  @Test
  void registerWithEncryptedValidatorKeystore(@TempDir final Path tempDir) throws IOException {
    final Path keyStoreFile = tempDir.resolve("keystore.json");
    KeyStoreLoader.saveToFile(keyStoreFile, VALIDATOR_KEYSTORE);

    final Path keystorePassword = tempDir.resolve("password.txt");
    Files.writeString(keystorePassword, PASSWORD);

    ValidatorKeyOptions validatorKeyOptions =
        buildValidatorKeyOptionsWithPasswordFile(keyStoreFile, keystorePassword);

    final DepositRegisterCommand depositRegisterCommand =
        new DepositRegisterCommand(
            shutdownFunction, envSupplier, commandSpec, commonParams, validatorKeyOptions, "");

    assertThatCode(depositRegisterCommand::run).doesNotThrowAnyException();

    verify(depositTransactionSender).sendDepositTransaction(any(), any(), any());
  }

  @Test
  void registerWithEncryptedValidatorKeystoreWithEnv(@TempDir final Path tempDir)
      throws IOException {
    final Path keyStoreFile = tempDir.resolve("keystore.json");
    KeyStoreLoader.saveToFile(keyStoreFile, VALIDATOR_KEYSTORE);

    ValidatorKeyOptions validatorKeyOptions = buildValidatorKeyOptionsWithEnv(keyStoreFile);

    final DepositRegisterCommand depositRegisterCommand =
        new DepositRegisterCommand(
            shutdownFunction, envSupplier, commandSpec, commonParams, validatorKeyOptions, "");

    assertThatCode(depositRegisterCommand::run).doesNotThrowAnyException();

    verify(depositTransactionSender).sendDepositTransaction(any(), any(), any());
  }

  private ValidatorKeyOptions buildValidatorKeyOptionsWithPasswordFile(
      final Path keyStoreFile, final Path keystorePassword) {
    ValidatorKeyOptions validatorKeyOptions = new ValidatorKeyOptions();
    validatorKeyOptions.validatorKeyStoreOptions = new ValidatorKeyStoreOptions();

    validatorKeyOptions.validatorKeyStoreOptions.validatorKeystoreFile = keyStoreFile.toFile();
    validatorKeyOptions.validatorKeyStoreOptions.validatorPasswordOptions =
        new ValidatorPasswordOptions();
    validatorKeyOptions
            .validatorKeyStoreOptions
            .validatorPasswordOptions
            .validatorKeystorePasswordFile =
        keystorePassword.toFile();
    return validatorKeyOptions;
  }

  private ValidatorKeyOptions buildValidatorKeyOptionsWithEnv(final Path keyStoreFile) {
    ValidatorKeyOptions validatorKeyOptions = new ValidatorKeyOptions();
    validatorKeyOptions.validatorKeyStoreOptions = new ValidatorKeyStoreOptions();

    validatorKeyOptions.validatorKeyStoreOptions.validatorKeystoreFile = keyStoreFile.toFile();
    validatorKeyOptions.validatorKeyStoreOptions.validatorPasswordOptions =
        new ValidatorPasswordOptions();
    validatorKeyOptions
            .validatorKeyStoreOptions
            .validatorPasswordOptions
            .validatorKeystorePasswordEnv =
        EXPECTED_ENV_VARIABLE;
    return validatorKeyOptions;
  }
}
