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

import com.google.common.io.Files;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.signers.bls.keystore.KeyStore;
import tech.pegasys.signers.bls.keystore.KeyStoreLoader;
import tech.pegasys.signers.bls.keystore.KeyStoreValidationException;
import tech.pegasys.signers.bls.keystore.model.KeyStoreData;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSSecretKey;
import tech.pegasys.teku.logging.StatusLogger;
import tech.pegasys.teku.util.config.TekuConfiguration;

public class KeystoresValidatorKeyProvider implements ValidatorKeyProvider {

  @Override
  public List<BLSKeyPair> loadValidatorKeys(final TekuConfiguration config) {
    final List<Pair<Path, Path>> keystorePasswordFilePairs =
        config.getValidatorKeystorePasswordFilePairs();
    checkNotNull(keystorePasswordFilePairs, "validator keystore and password pairs cannot be null");

    StatusLogger.STATUS_LOG.loadingValidators(keystorePasswordFilePairs.size());
    // return distinct loaded key pairs

    final ExecutorService executorService =
        Executors.newFixedThreadPool(Math.min(4, Runtime.getRuntime().availableProcessors()));
    try {
      final List<Future<Bytes32>> futures =
          keystorePasswordFilePairs.stream()
              .map(
                  pair ->
                      executorService.submit(
                          () -> loadBLSPrivateKey(pair.getLeft(), loadPassword(pair.getRight()))))
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
      password = Files.asCharSource(passwordFile.toFile(), UTF_8).readFirstLine();
      if (isEmpty(password)) {
        throw new IllegalArgumentException("Keystore password cannot be empty: " + passwordFile);
      }
    } catch (final FileNotFoundException e) {
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
