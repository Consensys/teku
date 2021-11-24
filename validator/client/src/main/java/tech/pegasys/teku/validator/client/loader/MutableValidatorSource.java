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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.signers.bls.keystore.KeyStore;
import tech.pegasys.signers.bls.keystore.KeyStoreLoader;
import tech.pegasys.signers.bls.keystore.KeyStoreValidationException;
import tech.pegasys.signers.bls.keystore.model.KeyStoreData;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSSecretKey;
import tech.pegasys.teku.core.signatures.LocalSigner;
import tech.pegasys.teku.core.signatures.Signer;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.service.serviceutils.layout.DataDirLayout;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.validator.api.KeyStoreFilesLocator;
import tech.pegasys.teku.validator.client.ValidatorClientService;
import tech.pegasys.teku.validator.client.restapi.apis.schema.PostKeyResult;

public class MutableValidatorSource implements ValidatorSource {

  private final Spec spec;
  private final boolean validatorKeystoreLockingEnabled;
  private final KeystoreLocker keystoreLocker;
  private final AsyncRunner asyncRunner;
  private final DataDirLayout dataDir;
  private LocalValidatorSource delegate;

  MutableValidatorSource(
      final Spec spec,
      final boolean validatorKeystoreLockingEnabled,
      final KeystoreLocker keystoreLocker,
      final AsyncRunner asyncRunner,
      final DataDirLayout dataDir,
      final LocalValidatorSource delegate) {
    this.spec = spec;
    this.validatorKeystoreLockingEnabled = validatorKeystoreLockingEnabled;
    this.keystoreLocker = keystoreLocker;
    this.asyncRunner = asyncRunner;
    this.delegate = delegate;
    this.dataDir = dataDir;
  }

  public static MutableValidatorSource create(
      final Spec spec,
      final boolean validatorKeystoreLockingEnabled,
      final KeystoreLocker keystoreLocker,
      final KeyStoreFilesLocator keyStoreFilesLocator,
      final AsyncRunner asyncRunner,
      final DataDirLayout dataDir) {
    return new MutableValidatorSource(
        spec,
        validatorKeystoreLockingEnabled,
        keystoreLocker,
        asyncRunner,
        dataDir,
        new LocalValidatorSource(
            spec,
            validatorKeystoreLockingEnabled,
            keystoreLocker,
            keyStoreFilesLocator,
            asyncRunner,
            false));
  }

  @Override
  public List<ValidatorProvider> getAvailableValidators() {
    return delegate.getAvailableValidators();
  }

  @Override
  public Map<PostKeyResult, Optional<Signer>> addValidator(
      KeyStoreData keyStoreData, String password) {
    final Map<PostKeyResult, Optional<Signer>> mapResult = new HashMap<>();
    try {
      final BLSKeyPair keyPair =
          new BLSKeyPair(
              BLSSecretKey.fromBytes(Bytes32.wrap(KeyStore.decrypt(password, keyStoreData))));
      if (!keyStoreData.getPubkey().equals(keyPair.getPublicKey().toSSZBytes())) {
        mapResult.put(
            PostKeyResult.error("Keystore declares incorrect public key."), Optional.empty());
        return mapResult;
      }
      final Path keystorePath = ValidatorClientService.getAlterableKeystorePath(dataDir);
      final Path passwordPath = ValidatorClientService.getAlterableKeystorePasswordPath(dataDir);
      final String validatorFileName =
          keyPair.getPublicKey().toSSZBytes().toUnprefixedHexString().toLowerCase();
      if (keystorePath.resolve(validatorFileName + ".json").toFile().exists()
          || passwordPath.resolve(validatorFileName + ".txt").toFile().exists()) {
        mapResult.put(PostKeyResult.duplicate(), Optional.empty());
        return mapResult;
      }
      try {
        KeyStoreLoader.saveToFile(keystorePath.resolve(validatorFileName + ".json"), keyStoreData);
        Files.writeString(passwordPath.resolve(validatorFileName + ".txt"), password);
        if (validatorKeystoreLockingEnabled) {
          keystoreLocker.lockKeystore(keystorePath.resolve(validatorFileName + ".json"));
        }
        mapResult.put(
            PostKeyResult.success(), Optional.of(new LocalSigner(spec, keyPair, asyncRunner)));
        return mapResult;
      } catch (InvalidConfigurationException e) {
        mapResult.put(PostKeyResult.error("Failed to lock keystore file."), Optional.empty());
        return mapResult;
      } catch (IOException e) {
        mapResult.put(PostKeyResult.error("Failed to save keystore file."), Optional.empty());
        return mapResult;
      }
    } catch (KeyStoreValidationException e) {
      mapResult.put(PostKeyResult.error("Invalid password."), Optional.empty());
      return mapResult;
    }
  }

  @Override
  public boolean canAddValidator() {
    return true;
  }
}
