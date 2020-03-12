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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.artemis.util.async.SafeFuture.completedFuture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
import tech.pegasys.artemis.bls.keystore.KeyStore;
import tech.pegasys.artemis.bls.keystore.KeyStoreLoader;
import tech.pegasys.artemis.bls.keystore.model.Cipher;
import tech.pegasys.artemis.bls.keystore.model.KdfParam;
import tech.pegasys.artemis.bls.keystore.model.KeyStoreData;
import tech.pegasys.artemis.bls.keystore.model.SCryptParam;
import tech.pegasys.artemis.cli.deposit.DepositRegisterCommand.ValidatorKeyOptions;
import tech.pegasys.artemis.cli.deposit.DepositRegisterCommand.ValidatorKeyStoreOptions;
import tech.pegasys.artemis.services.powchain.DepositTransactionSender;

class DepositRegisterCommandTest {
  private static final Consumer<Integer> shutdownFunction = status -> {};
  private static final String PASSWORD = "testpassword";
  private static final Bytes BLS_PRIVATE_KEY =
      Bytes48.leftPad(
          Bytes.fromHexString(
              "0x000000000019d6689c085ae165831e934ff763ae46a2a6c172b3f1b60a8ce26f"));
  private static final Bytes BLS_PUB_KEY =
      Bytes.fromHexString(
          "9612d7a727c9d0a22e185a1c768478dfe919cada9266988cb32359c11f2b7b27f4ae4040902382ae2910c15e2b420d07");
  private static final Bytes32 SALT =
      Bytes32.fromHexString("d4e56740f876aef8c010b86a40d5f56745a118d0906a34e69aec8c0db1cb8fa3");
  private static final Bytes AES_IV_PARAM = Bytes.fromHexString("264daa3f303d7259501c93d997d84fe6");
  private static final Cipher CIPHER = new Cipher(AES_IV_PARAM);
  private static final KdfParam KDF_PARAM = new SCryptParam(32, 262144, 1, 8, SALT);
  private static final KeyStoreData VALIDATOR_KEYSTORE =
      KeyStore.encrypt(BLS_PRIVATE_KEY, PASSWORD, "", KDF_PARAM, CIPHER);
  private CommonParams commonParams;
  private CommandLine.Model.CommandSpec commandSpec;

  @BeforeEach
  void setUp() {
    commonParams = mock(CommonParams.class);
    commandSpec = mock(CommandLine.Model.CommandSpec.class);
    final CommandLine commandLine = mock(CommandLine.class);
    final DepositTransactionSender depositTransactionSender = mock(DepositTransactionSender.class);

    when(commandSpec.commandLine()).thenReturn(commandLine);
    when(commonParams.createTransactionSender()).thenReturn(depositTransactionSender);
    when(depositTransactionSender.sendDepositTransaction(any(), any(), any()))
        .thenReturn(completedFuture(null));
  }

  @Test
  void registerWithEncryptedValidatorKeystore(@TempDir final Path tempDir) throws IOException {
    final Path keyStoreFile = Files.createTempFile(tempDir, "keystore", ".json");
    KeyStoreLoader.saveToFile(keyStoreFile, VALIDATOR_KEYSTORE);

    final Path keystorePassword = Files.createTempFile(tempDir, "password", ".txt");
    Files.writeString(keystorePassword, PASSWORD);

    ValidatorKeyOptions validatorKeyOptions =
        buildValidatorKeyOptions(keyStoreFile, keystorePassword);

    final DepositRegisterCommand depositRegisterCommand =
        new DepositRegisterCommand(
            shutdownFunction, commandSpec, commonParams, validatorKeyOptions, "");

    depositRegisterCommand.run();
  }

  private ValidatorKeyOptions buildValidatorKeyOptions(
      final Path keyStoreFile, final Path keystorePassword) {
    ValidatorKeyOptions validatorKeyOptions = new ValidatorKeyOptions();
    validatorKeyOptions.validatorKeyStoreOptions = new ValidatorKeyStoreOptions();

    validatorKeyOptions.validatorKeyStoreOptions.validatorKeystoreFile = keyStoreFile.toFile();
    validatorKeyOptions.validatorKeyStoreOptions.validatorKeystorePasswordFile =
        keystorePassword.toFile();
    return validatorKeyOptions;
  }
}
