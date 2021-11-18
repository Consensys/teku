/*
 * Copyright 2021 ConsenSys AG.
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

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isEmpty;

import com.google.common.base.MoreObjects;
import com.google.common.base.Throwables;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import tech.pegasys.signers.bls.keystore.KeyStore;
import tech.pegasys.signers.bls.keystore.KeyStoreLoader;
import tech.pegasys.signers.bls.keystore.KeyStoreValidationException;
import tech.pegasys.signers.bls.keystore.model.KeyStoreData;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSecretKey;
import tech.pegasys.teku.core.signatures.LocalSigner;
import tech.pegasys.teku.core.signatures.Signer;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.validator.api.KeyStoreFilesLocator;

public class LocalValidatorSource implements ValidatorSource {

  private final Spec spec;
  private final boolean validatorKeystoreLockingEnabled;
  private final KeystoreLocker keystoreLocker;
  private final AsyncRunner asyncRunner;
  private final KeyStoreFilesLocator keyStoreFilesLocator;
  private final boolean readOnly;

  public LocalValidatorSource(
      final Spec spec,
      final boolean validatorKeystoreLockingEnabled,
      final KeystoreLocker keystoreLocker,
      final KeyStoreFilesLocator keyStoreFilesLocator,
      final AsyncRunner asyncRunner,
      final boolean readOnly) {
    this.spec = spec;
    this.validatorKeystoreLockingEnabled = validatorKeystoreLockingEnabled;
    this.keystoreLocker = keystoreLocker;
    this.asyncRunner = asyncRunner;
    this.keyStoreFilesLocator = keyStoreFilesLocator;
    this.readOnly = readOnly;
  }

  @Override
  public List<ValidatorProvider> getAvailableValidators() {
    final List<Pair<Path, Path>> filePairs = keyStoreFilesLocator.parse();
    return filePairs.stream().map(this::createValidatorProvider).collect(toList());
  }

  private ValidatorProvider createValidatorProvider(
      final Pair<Path, Path> keystorePasswordFilePair) {
    final Path keystorePath = keystorePasswordFilePair.getLeft();
    final Path passwordPath = keystorePasswordFilePair.getRight();
    try {
      final KeyStoreData keyStoreData = KeyStoreLoader.loadFromFile(keystorePath);
      final BLSPublicKey publicKey =
          BLSPublicKey.fromBytesCompressedValidate(Bytes48.wrap(keyStoreData.getPubkey()));
      final String password = loadPassword(passwordPath);
      return new LocalValidatorProvider(
          spec, keyStoreData, keystorePath, publicKey, password, readOnly);
    } catch (final KeyStoreValidationException e) {
      if (Throwables.getRootCause(e) instanceof FileNotFoundException) {
        throw new InvalidConfigurationException(e.getMessage(), e);
      }
      throw new InvalidConfigurationException("Invalid keystore: " + keystorePath, e);
    }
  }

  private String loadPassword(final Path passwordFile) {
    final String password;
    try {
      password = Files.readString(passwordFile, UTF_8);
      if (isEmpty(password)) {
        throw new InvalidConfigurationException(
            "Keystore password cannot be empty: " + passwordFile);
      }
    } catch (final FileNotFoundException | NoSuchFileException e) {
      throw new InvalidConfigurationException(
          "Keystore password file not found: " + passwordFile, e);
    } catch (final IOException e) {
      final String errorMessage =
          String.format(
              "Unexpected IO error while reading keystore password file [%s]: %s",
              passwordFile, e.getMessage());
      throw new InvalidConfigurationException(errorMessage, e);
    }
    return password;
  }

  private class LocalValidatorProvider implements ValidatorProvider {

    private final Spec spec;
    private final KeyStoreData keyStoreData;
    private final Path keystoreFile;
    private final BLSPublicKey publicKey;
    private final String password;
    private final boolean readOnly;

    private LocalValidatorProvider(
        final Spec spec,
        final KeyStoreData keyStoreData,
        final Path keystoreFile,
        final BLSPublicKey publicKey,
        final String password,
        final boolean readOnly) {
      this.spec = spec;
      this.keyStoreData = keyStoreData;
      this.keystoreFile = keystoreFile;
      this.publicKey = publicKey;
      this.password = password;
      this.readOnly = readOnly;
    }

    @Override
    public BLSPublicKey getPublicKey() {
      return publicKey;
    }

    @Override
    public boolean isReadOnly() {
      return readOnly;
    }

    @Override
    public Signer createSigner() {
      final BLSKeyPair keyPair = new BLSKeyPair(BLSSecretKey.fromBytes(loadBLSPrivateKey()));
      if (!keyPair.getPublicKey().equals(getPublicKey())) {
        throw new InvalidConfigurationException(
            String.format(
                "Keystore declares incorrect public key. Was %s but expected %s",
                getPublicKey(), keyPair.getPublicKey()));
      }
      return new LocalSigner(spec, keyPair, asyncRunner);
    }

    private Bytes32 loadBLSPrivateKey() {
      try {
        if (validatorKeystoreLockingEnabled) {
          keystoreLocker.lockKeystore(keystoreFile);
        }
        return Bytes32.wrap(KeyStore.decrypt(password, keyStoreData));
      } catch (final KeyStoreValidationException e) {
        throw new InvalidConfigurationException(
            "Failed to decrypt keystore " + keystoreFile + ". Check the password is correct.", e);
      }
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("keystoreFile", keystoreFile)
          .add("publicKey", publicKey)
          .toString();
    }
  }
}
