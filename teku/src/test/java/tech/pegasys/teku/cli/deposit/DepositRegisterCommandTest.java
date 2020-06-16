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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.util.async.SafeFuture.completedFuture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
import tech.pegasys.signers.bls.keystore.KeyStore;
import tech.pegasys.signers.bls.keystore.KeyStoreLoader;
import tech.pegasys.signers.bls.keystore.model.Cipher;
import tech.pegasys.signers.bls.keystore.model.KdfParam;
import tech.pegasys.signers.bls.keystore.model.KeyStoreData;
import tech.pegasys.signers.bls.keystore.model.SCryptParam;
import tech.pegasys.teku.cli.deposit.DepositRegisterCommand.ValidatorKeyOptions;
import tech.pegasys.teku.cli.deposit.DepositRegisterCommand.ValidatorKeyStoreOptions;
import tech.pegasys.teku.cli.deposit.DepositRegisterCommand.ValidatorPasswordOptions;

class DepositRegisterCommandTest {
  private static final Consumer<Integer> shutdownFunction = status -> {};
  private static final String PASSWORD = "testpassword";
  private static final String EXPECTED_ENV_VARIABLE = "TEST_ENV";
  private static final Function<String, String> envSupplier =
      s -> EXPECTED_ENV_VARIABLE.equals(s) ? PASSWORD : null;
  private static final Bytes BLS_PRIVATE_KEY =
      Bytes.fromHexString("0x19d6689c085ae165831e934ff763ae46a2a6c172b3f1b60a8ce26f", 32);
  private static final Bytes BLS_PUB_KEY =
      Bytes.fromHexString(
          "9612d7a727c9d0a22e185a1c768478dfe919cada9266988cb32359c11f2b7b27f4ae4040902382ae2910c15e2b420d07");
  private static final Bytes32 SALT =
      Bytes32.fromHexString("d4e56740f876aef8c010b86a40d5f56745a118d0906a34e69aec8c0db1cb8fa3");
  private static final Bytes AES_IV_PARAM = Bytes.fromHexString("264daa3f303d7259501c93d997d84fe6");
  private static final Cipher CIPHER = new Cipher(AES_IV_PARAM);
  private static final KdfParam KDF_PARAM = new SCryptParam(32, 262144, 1, 8, SALT);
  private static final KeyStoreData VALIDATOR_KEYSTORE =
      KeyStore.encrypt(BLS_PRIVATE_KEY, BLS_PUB_KEY, PASSWORD, "", KDF_PARAM, CIPHER);
  private RegisterParams registerParams;
  private CommandLine.Model.CommandSpec commandSpec;
  private RegisterAction registerAction;

  @BeforeEach
  void setUp() {
    registerParams = mock(RegisterParams.class);
    commandSpec = mock(CommandLine.Model.CommandSpec.class);
    final CommandLine commandLine = mock(CommandLine.class);
    registerAction = mock(RegisterAction.class);

    when(commandSpec.commandLine()).thenReturn(commandLine);
    when(registerParams.createRegisterAction(anyBoolean())).thenReturn(registerAction);
    when(registerAction.sendDeposit(any(), any())).thenReturn(completedFuture(null));
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
            shutdownFunction, envSupplier, commandSpec, registerParams, validatorKeyOptions, "");

    assertThatCode(depositRegisterCommand::run).doesNotThrowAnyException();

    verify(registerAction).sendDeposit(any(), any());
  }

  @Test
  void registerWithEncryptedValidatorKeystoreWithEnv(@TempDir final Path tempDir)
      throws IOException {
    final Path keyStoreFile = tempDir.resolve("keystore.json");
    KeyStoreLoader.saveToFile(keyStoreFile, VALIDATOR_KEYSTORE);

    ValidatorKeyOptions validatorKeyOptions = buildValidatorKeyOptionsWithEnv(keyStoreFile);

    final DepositRegisterCommand depositRegisterCommand =
        new DepositRegisterCommand(
            shutdownFunction, envSupplier, commandSpec, registerParams, validatorKeyOptions, "");

    assertThatCode(depositRegisterCommand::run).doesNotThrowAnyException();

    verify(registerAction).sendDeposit(any(), any());
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
