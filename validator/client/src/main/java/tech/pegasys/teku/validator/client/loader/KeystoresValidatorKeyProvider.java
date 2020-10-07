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

package tech.pegasys.teku.validator.client.loader;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isEmpty;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.signers.bls.keystore.KeyStore;
import tech.pegasys.signers.bls.keystore.KeyStoreLoader;
import tech.pegasys.signers.bls.keystore.KeyStoreValidationException;
import tech.pegasys.signers.bls.keystore.model.KeyStoreData;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSSecretKey;
import tech.pegasys.teku.infrastructure.logging.StatusLogger;
import tech.pegasys.teku.validator.api.ValidatorConfig;

public class KeystoresValidatorKeyProvider implements ValidatorKeyProvider {
  private final KeystoreLocker keystoreLocker;
  private final ValidatorConfig config;

  public KeystoresValidatorKeyProvider(
      final KeystoreLocker keystoreLocker, final ValidatorConfig config) {
    this.keystoreLocker = keystoreLocker;
    this.config = config;
  }

  @Override
  public List<BLSKeyPair> loadValidatorKeys() {
    final List<Pair<Path, Path>> keystorePasswordFilePairs =
        config.getValidatorKeystorePasswordFilePairs();
    checkNotNull(keystorePasswordFilePairs, "validator keystore and password pairs cannot be null");

    final int totalValidatorCount = keystorePasswordFilePairs.size();
    StatusLogger.STATUS_LOG.loadingValidators(totalValidatorCount);
    // return distinct loaded key pairs

    final ExecutorService executorService =
        Executors.newFixedThreadPool(Math.min(4, Runtime.getRuntime().availableProcessors()));
    try {
      final AtomicInteger numberOfLoadedKeys = new AtomicInteger(0);
      final List<Future<Bytes32>> futures =
          keystorePasswordFilePairs.stream()
              .map(
                  pair ->
                      executorService.submit(
                          () -> {
                            Bytes32 privateKey =
                                loadBLSPrivateKey(pair.getLeft(), loadPassword(pair.getRight()));
                            int loadedValidatorCount = numberOfLoadedKeys.incrementAndGet();
                            if (loadedValidatorCount % 10 == 0) {
                              StatusLogger.STATUS_LOG.atLoadedValidatorNumber(
                                  loadedValidatorCount, totalValidatorCount);
                            }
                            return privateKey;
                          }))
              .collect(toList());

      Set<Bytes32> result = new HashSet<>();
      for (Future<Bytes32> future : futures) {
        result.add(future.get());
      }
      return result.stream()
          .map(privKey -> new BLSKeyPair(BLSSecretKey.fromBytes(privKey)))
          .collect(toList());
    } catch (InterruptedException e) {
      throw new RuntimeException("Interrupted while attempting to load validator key files", e);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof RuntimeException) {
        throw (RuntimeException) e.getCause();
      }
      throw new RuntimeException("Unable to load validator key files", e);
    } finally {
      executorService.shutdownNow();
    }
  }

  private Bytes32 loadBLSPrivateKey(final Path keystoreFile, final String password) {
    try {
      if (config.isValidatorKeystoreLockingEnabled()) {
        keystoreLocker.lockKeystore(keystoreFile);
      }
      final KeyStoreData keyStoreData = KeyStoreLoader.loadFromFile(keystoreFile);
      if (!KeyStore.validatePassword(password, keyStoreData)) {
        throw new IllegalArgumentException("Invalid keystore password: " + keystoreFile);
      }
      return Bytes32.wrap(KeyStore.decrypt(password, keyStoreData));
    } catch (final KeyStoreValidationException e) {
      throw new IllegalArgumentException(e.getMessage(), e);
    }
  }

  private String loadPassword(final Path passwordFile) {
    final String password;
    try {
      password = Files.readString(passwordFile, UTF_8);
      if (isEmpty(password)) {
        throw new IllegalArgumentException("Keystore password cannot be empty: " + passwordFile);
      }
    } catch (final FileNotFoundException | NoSuchFileException e) {
      throw new IllegalArgumentException("Keystore password file not found: " + passwordFile, e);
    } catch (final IOException e) {
      final String errorMessage =
          format(
              "Unexpected IO error while reading keystore password file [%s]: %s",
              passwordFile, e.getMessage());
      throw new UncheckedIOException(errorMessage, e);
    }
    return password;
  }
}
