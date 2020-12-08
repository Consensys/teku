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

package tech.pegasys.teku.cli.subcommand.internal.validator.options;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.IntConsumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.web3j.crypto.CipherException;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.WalletUtils;
import org.web3j.utils.Numeric;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import tech.pegasys.teku.cli.subcommand.internal.validator.tools.ConsoleAdapter;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.util.config.Constants;

class DepositOptionsTest {
  private static final String ETH1_PRIVATE_KEY =
      "8f2a55949038a9610f50fb23b5883af3b4ecb3c3bb792cbcefbd1542c692be63";
  private static final ECKeyPair EXPECTED_EC_KEYPAIR =
      ECKeyPair.create(Numeric.toBigInt(ETH1_PRIVATE_KEY));
  private static final IntConsumer SHUTDOWN_FUNCTION = status -> {};
  private static final String PASSWORD = "test123";
  private CommandSpec commandSpec = mock(CommandSpec.class);
  private ConsoleAdapter consoleAdapter = mock(ConsoleAdapter.class);

  @AfterEach
  public void tearDown() {
    Constants.setConstants("minimal");
  }

  @Test
  void eth1PrivateKeyReturnsCredential() {
    final Eth1PrivateKeyOptions eth1PrivateKeyOptions = new Eth1PrivateKeyOptions();
    eth1PrivateKeyOptions.eth1PrivateKey = ETH1_PRIVATE_KEY;
    final DepositOptions depositOptions =
        new DepositOptions(commandSpec, eth1PrivateKeyOptions, SHUTDOWN_FUNCTION, consoleAdapter);
    final Credentials eth1Credentials = depositOptions.getEth1Credentials();
    assertThat(eth1Credentials.getEcKeyPair()).isEqualTo(EXPECTED_EC_KEYPAIR);
  }

  @Test
  void eth1EncryptedKeystoreReturnsCredential(@TempDir final Path tempDir)
      throws IOException, CipherException {
    // create v3 wallet
    final String keystoreFileName =
        WalletUtils.generateWalletFile(PASSWORD, EXPECTED_EC_KEYPAIR, tempDir.toFile(), true);
    final File keystoreFile = tempDir.resolve(keystoreFileName).toFile();
    // create password file
    final File passwordFile =
        Files.writeString(tempDir.resolve("password.txt"), "test123").toFile();

    final Eth1PrivateKeyOptions.Eth1EncryptedKeystoreOptions keystoreOptions =
        new Eth1PrivateKeyOptions.Eth1EncryptedKeystoreOptions();
    keystoreOptions.eth1KeystoreFile = keystoreFile;
    keystoreOptions.eth1KeystorePasswordFile = passwordFile;

    final Eth1PrivateKeyOptions eth1PrivateKeyOptions = new Eth1PrivateKeyOptions();
    eth1PrivateKeyOptions.keystoreOptions = keystoreOptions;

    final DepositOptions depositOptions =
        new DepositOptions(commandSpec, eth1PrivateKeyOptions, SHUTDOWN_FUNCTION, consoleAdapter);
    final Credentials eth1Credentials = depositOptions.getEth1Credentials();
    assertThat(eth1Credentials.getEcKeyPair()).isEqualTo(EXPECTED_EC_KEYPAIR);
  }

  @Test
  void nonExistentEth1EncryptedKeystoreThrowsError(@TempDir final Path tempDir) {
    final Eth1PrivateKeyOptions.Eth1EncryptedKeystoreOptions keystoreOptions =
        new Eth1PrivateKeyOptions.Eth1EncryptedKeystoreOptions();
    keystoreOptions.eth1KeystoreFile = tempDir.resolve("nonExistent").toFile();
    keystoreOptions.eth1KeystorePasswordFile = tempDir.resolve("nonExistent").toFile();

    final Eth1PrivateKeyOptions eth1PrivateKeyOptions = new Eth1PrivateKeyOptions();
    eth1PrivateKeyOptions.keystoreOptions = keystoreOptions;

    final DepositOptions depositOptions =
        new DepositOptions(commandSpec, eth1PrivateKeyOptions, SHUTDOWN_FUNCTION, consoleAdapter);

    when(commandSpec.commandLine()).thenReturn(mock(CommandLine.class));
    assertThatExceptionOfType(CommandLine.ParameterException.class)
        .isThrownBy(depositOptions::getEth1Credentials)
        .withMessage("Error: File not found: " + keystoreOptions.eth1KeystoreFile);
  }

  @Test
  void validJsonNotComplaintWithKeystoreFormatThrowsError(@TempDir final Path tempDir)
      throws IOException {
    final Eth1PrivateKeyOptions.Eth1EncryptedKeystoreOptions keystoreOptions =
        new Eth1PrivateKeyOptions.Eth1EncryptedKeystoreOptions();
    keystoreOptions.eth1KeystoreFile =
        Files.writeString(tempDir.resolve("v3.json"), "{test:123}").toFile();
    keystoreOptions.eth1KeystorePasswordFile =
        Files.writeString(tempDir.resolve("password.txt"), "test123").toFile();

    final Eth1PrivateKeyOptions eth1PrivateKeyOptions = new Eth1PrivateKeyOptions();
    eth1PrivateKeyOptions.keystoreOptions = keystoreOptions;

    final DepositOptions depositOptions =
        new DepositOptions(commandSpec, eth1PrivateKeyOptions, SHUTDOWN_FUNCTION, consoleAdapter);

    when(commandSpec.commandLine()).thenReturn(mock(CommandLine.class));
    assertThatExceptionOfType(CommandLine.ParameterException.class)
        .isThrownBy(depositOptions::getEth1Credentials)
        .withMessage(
            "Error: Unable to decrypt Eth1 keystore [%s] : Wallet version is not supported",
            keystoreOptions.eth1KeystoreFile);
  }

  @Test
  void invalidJsonEth1EncryptedKeystoreThrowsError(@TempDir final Path tempDir) throws IOException {
    final Eth1PrivateKeyOptions.Eth1EncryptedKeystoreOptions keystoreOptions =
        new Eth1PrivateKeyOptions.Eth1EncryptedKeystoreOptions();
    keystoreOptions.eth1KeystoreFile =
        Files.writeString(tempDir.resolve("v3.json"), "invalidfilecontents").toFile();
    keystoreOptions.eth1KeystorePasswordFile =
        Files.writeString(tempDir.resolve("password.txt"), "test123").toFile();

    final Eth1PrivateKeyOptions eth1PrivateKeyOptions = new Eth1PrivateKeyOptions();
    eth1PrivateKeyOptions.keystoreOptions = keystoreOptions;

    final DepositOptions depositOptions =
        new DepositOptions(commandSpec, eth1PrivateKeyOptions, SHUTDOWN_FUNCTION, consoleAdapter);

    when(commandSpec.commandLine()).thenReturn(mock(CommandLine.class));
    assertThatExceptionOfType(CommandLine.ParameterException.class)
        .isThrownBy(depositOptions::getEth1Credentials)
        .withMessageStartingWith(
            "Error: Unexpected IO Error while reading Eth1 keystore [%s] : Unrecognized token ",
            keystoreOptions.eth1KeystoreFile);
  }

  @Test
  public void shouldUseMaxEffectiveBalanceAsDefaultAmount() {
    Constants.MAX_EFFECTIVE_BALANCE = UInt64.valueOf(12345678);
    final Eth1PrivateKeyOptions eth1PrivateKeyOptions = new Eth1PrivateKeyOptions();
    eth1PrivateKeyOptions.eth1PrivateKey = ETH1_PRIVATE_KEY;
    final DepositOptions depositOptions =
        new DepositOptions(commandSpec, eth1PrivateKeyOptions, SHUTDOWN_FUNCTION, consoleAdapter);

    assertThat(depositOptions.getAmount()).isEqualTo(Constants.MAX_EFFECTIVE_BALANCE);
  }
}
