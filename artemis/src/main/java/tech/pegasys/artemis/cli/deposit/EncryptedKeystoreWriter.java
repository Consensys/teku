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

import static tech.pegasys.artemis.util.crypto.SecureRandomProvider.createSecureRandom;
import static tech.pegasys.teku.logging.StatusLogger.STATUS_LOG;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.bls.keystore.KeyStore;
import tech.pegasys.artemis.bls.keystore.KeyStoreLoader;
import tech.pegasys.artemis.bls.keystore.KeyStoreValidationException;
import tech.pegasys.artemis.bls.keystore.model.Cipher;
import tech.pegasys.artemis.bls.keystore.model.CipherFunction;
import tech.pegasys.artemis.bls.keystore.model.KdfParam;
import tech.pegasys.artemis.bls.keystore.model.KeyStoreData;
import tech.pegasys.artemis.bls.keystore.model.SCryptParam;
import tech.pegasys.artemis.util.bls.BLSKeyPair;

public class EncryptedKeystoreWriter implements KeysWriter {
  private final String validatorKeyPassword;
  private final String withdrawalKeyPassword;
  private final Path outputPath;
  private final AtomicInteger counter = new AtomicInteger(0);

  public EncryptedKeystoreWriter(
      final String validatorKeyPassword,
      final String withdrawalKeyPassword,
      final Path outputPath) {
    this.validatorKeyPassword = validatorKeyPassword;
    this.withdrawalKeyPassword = withdrawalKeyPassword;
    this.outputPath = outputPath;
  }

  @Override
  public void writeKeys(final BLSKeyPair validatorKey, final BLSKeyPair withdrawalKey)
      throws UncheckedIOException, KeyStoreValidationException {
    final Path keystoreDirectory = createKeystoreDirectory();

    final KeyStoreData validatorKeyStoreData =
        generateKeystoreData(validatorKey, validatorKeyPassword);
    final KeyStoreData withdrawalKeyStoreData =
        generateKeystoreData(withdrawalKey, withdrawalKeyPassword);

    saveKeyStore(keystoreDirectory.resolve("validator_keystore.json"), validatorKeyStoreData);
    saveKeyStore(keystoreDirectory.resolve("withdrawal_keystore.json"), withdrawalKeyStoreData);
  }

  private Path createKeystoreDirectory() {
    final Path keystoreDirectory = outputPath.resolve("validator_" + counter.incrementAndGet());
    try {
      return Files.createDirectories(keystoreDirectory);
    } catch (IOException e) {
      STATUS_LOG.log(
          Level.FATAL,
          "Unable to create directory [" + keystoreDirectory + "] : " + e.getMessage());
      throw new UncheckedIOException(e);
    }
  }

  private KeyStoreData generateKeystoreData(final BLSKeyPair key, final String password) {
    final KdfParam kdfParam = new SCryptParam(32, Bytes32.random(createSecureRandom()));
    final Cipher cipher =
        new Cipher(CipherFunction.AES_128_CTR, Bytes.random(16, createSecureRandom()));
    return KeyStore.encrypt(
        key.getSecretKey().getSecretKey().toBytes(), password, "", kdfParam, cipher);
  }

  private void saveKeyStore(final Path outputPath, final KeyStoreData keyStoreData) {
    try {
      KeyStoreLoader.saveToFile(outputPath, keyStoreData);
    } catch (final IOException e) {
      STATUS_LOG.log(
          Level.FATAL, "Unable to save keystore file [" + outputPath + "] : " + e.getMessage());
      throw new UncheckedIOException(e);
    }
  }
}
