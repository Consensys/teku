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

import static java.util.stream.Collectors.toList;

import java.util.List;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.core.signatures.LocalSigner;
import tech.pegasys.teku.core.signatures.Signer;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;

public class LocalValidatorSource implements ValidatorSource {
  private final KeystoresValidatorKeyProvider keyProvider;
  private final AsyncRunner asyncRunner;

  public LocalValidatorSource(
      final KeystoresValidatorKeyProvider keyProvider, final AsyncRunner asyncRunner) {
    this.keyProvider = keyProvider;
    this.asyncRunner = asyncRunner;
  }

  @Override
  public List<ValidatorProvider> getAvailableValidators() {
    return keyProvider.loadValidatorKeys().stream()
        .map(LocalValidatorProvider::new)
        .collect(toList());
  }

  private class LocalValidatorProvider implements ValidatorProvider {
    private final BLSKeyPair keyPair;

    private LocalValidatorProvider(final BLSKeyPair keyPair) {
      this.keyPair = keyPair;
    }

    @Override
    public BLSPublicKey getPublicKey() {
      return keyPair.getPublicKey();
    }

    @Override
    public Signer createSigner() {
      return new LocalSigner(keyPair, asyncRunner);
    }
  }
}
