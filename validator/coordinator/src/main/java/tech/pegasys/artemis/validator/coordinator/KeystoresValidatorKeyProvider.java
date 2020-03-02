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

package tech.pegasys.artemis.validator.coordinator;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.isEmpty;

import com.google.common.io.Files;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.artemis.bls.keystore.KeyStore;
import tech.pegasys.artemis.bls.keystore.KeyStoreLoader;
import tech.pegasys.artemis.bls.keystore.model.KeyStoreData;
import tech.pegasys.artemis.util.bls.BLSKeyPair;
import tech.pegasys.artemis.util.config.ArtemisConfiguration;
import tech.pegasys.artemis.util.mikuli.KeyPair;
import tech.pegasys.artemis.util.mikuli.SecretKey;

public class KeystoresValidatorKeyProvider implements ValidatorKeyProvider {
  static final int KEY_LENGTH = 48;

  @Override
  public List<BLSKeyPair> loadValidatorKeys(final ArtemisConfiguration config) {
    final List<Pair<Path, Path>> keystorePasswordFilePairs =
        config.getValidatorKeystorePasswordFilePairs();
    checkNotNull(keystorePasswordFilePairs, "validator keystore and password pairs cannot be null");

    // return distinct loaded key pairs
    return new ArrayList<>(
        keystorePasswordFilePairs.stream()
            .map(
                pair -> {
                  final String password = loadPassword(pair.getRight());
                  final KeyStoreData keyStoreData = KeyStoreLoader.loadFromFile(pair.getLeft());
                  if (!KeyStore.validatePassword(password, keyStoreData)) {
                    throw new RuntimeException("Invalid keystore password: " + pair.getLeft());
                  }
                  final Bytes privKey = KeyStore.decrypt(password, keyStoreData);
                  return new BLSKeyPair(new KeyPair(SecretKey.fromBytes(padLeft(privKey))));
                })
            .collect(
                toMap(
                    blsKeyPair -> blsKeyPair.getPublicKey().toString(),
                    Function.identity(),
                    (dupKey1, dupKey2) -> dupKey2))
            .values());
  }

  private String loadPassword(final Path passwordFile) {
    final String password;
    try {
      password = Files.asCharSource(passwordFile.toFile(), UTF_8).readFirstLine();
      if (isEmpty(password)) {
        throw new RuntimeException("Keystore password cannot be empty: " + passwordFile);
      }
    } catch (final FileNotFoundException e) {
      throw new UncheckedIOException("Keystore password file not found: " + passwordFile, e);
    } catch (final IOException e) {
      throw new UncheckedIOException(
          format(
              "Unexpected IO error while reading keystore password file [%s]: %s",
              passwordFile, e.getMessage()),
          e);
    }
    return password;
  }

  private Bytes padLeft(Bytes input) {
    return Bytes.concatenate(Bytes.wrap(new byte[KEY_LENGTH - input.size()]), input);
  }
}
