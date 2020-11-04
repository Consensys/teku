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

package tech.pegasys.teku.cli.subcommand.internal.validator.tools;

import static tech.pegasys.teku.infrastructure.logging.StatusLogger.STATUS_LOG;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.signers.bls.keystore.KeyStore;
import tech.pegasys.signers.bls.keystore.KeyStoreLoader;
import tech.pegasys.signers.bls.keystore.KeyStoreValidationException;
import tech.pegasys.signers.bls.keystore.model.Cipher;
import tech.pegasys.signers.bls.keystore.model.CipherFunction;
import tech.pegasys.signers.bls.keystore.model.KdfParam;
import tech.pegasys.signers.bls.keystore.model.KeyStoreData;
import tech.pegasys.signers.bls.keystore.model.SCryptParam;
import tech.pegasys.teku.bls.BLSKeyPair;

public class EncryptedKeystoreWriter implements KeysWriter {

  private final SecureRandom secureRandom;
  private final String validatorKeyPassword;
  private final String withdrawalKeyPassword;
  private final Path outputPath;
  private final Consumer<String> commandOutput;

  public EncryptedKeystoreWriter(
      final SecureRandom secureRandom,
      final String validatorKeyPassword,
      final String withdrawalKeyPassword,
      final Path outputPath,
      final Consumer<String> commandOutput) {
    this.secureRandom = secureRandom;
    this.validatorKeyPassword = validatorKeyPassword;
    this.withdrawalKeyPassword = withdrawalKeyPassword;
    this.outputPath = outputPath;
    this.commandOutput = commandOutput;
    createKeystoreDirectory();
  }

  @Override
  public void writeKeys(final BLSKeyPair validatorKey, final BLSKeyPair withdrawalKey)
      throws UncheckedIOException, KeyStoreValidationException {

    commandOutput.accept(
        "Generating encrypted keystore for validator key ["
            + validatorKey.getPublicKey().toString()
            + "]");
    final KeyStoreData validatorKeyStoreData =
        generateKeystoreData(validatorKey, validatorKeyPassword);

    commandOutput.accept(
        "Generating encrypted keystore for withdrawal key ["
            + withdrawalKey.getPublicKey().toString()
            + "]");
    final KeyStoreData withdrawalKeyStoreData =
        generateKeystoreData(withdrawalKey, withdrawalKeyPassword);

    final String validatorFileName =
        validatorKey.getPublicKey().toAbbreviatedString() + "_validator.json";
    final String withdrawalFileName =
        validatorKey.getPublicKey().toAbbreviatedString() + "_withdrawal.json";

    saveKeyStore(outputPath.resolve(validatorFileName), validatorKeyStoreData);
    commandOutput.accept(
        "Withdrawal keystore [" + outputPath.resolve(validatorFileName) + "] saved successfully.");

    saveKeyStore(outputPath.resolve(withdrawalFileName), withdrawalKeyStoreData);
    commandOutput.accept(
        "Validator keystore [" + outputPath.resolve(withdrawalFileName) + "] saved successfully.");
  }

  private void createKeystoreDirectory() {
    try {
      Files.createDirectories(outputPath);
    } catch (IOException e) {
      STATUS_LOG.validatorDepositEncryptedKeystoreWriterFailure(
          "Error: Unable to create directory [{}] : {}", outputPath, e.getMessage());
      throw new UncheckedIOException(e);
    }
  }

  private KeyStoreData generateKeystoreData(final BLSKeyPair key, final String password) {
    final KdfParam kdfParam = new SCryptParam(32, Bytes32.random(secureRandom));
    final Cipher cipher = new Cipher(CipherFunction.AES_128_CTR, Bytes.random(16, secureRandom));
    return KeyStore.encrypt(
        key.getSecretKey().toBytes(),
        key.getPublicKey().toBytesCompressed(),
        password,
        "",
        kdfParam,
        cipher);
  }

  private void saveKeyStore(final Path outputPath, final KeyStoreData keyStoreData) {
    try {
      KeyStoreLoader.saveToFile(outputPath, keyStoreData);
    } catch (final IOException e) {
      STATUS_LOG.validatorDepositEncryptedKeystoreWriterFailure(
          "Error: Unable to save keystore file [{}] : {}", outputPath, e.getMessage());
      throw new UncheckedIOException(e);
    }
  }
}
