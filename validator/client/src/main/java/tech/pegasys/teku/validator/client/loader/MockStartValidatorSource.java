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
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.signers.bls.keystore.model.KeyStoreData;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.core.signatures.LocalSigner;
import tech.pegasys.teku.core.signatures.Signer;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.interop.MockStartValidatorKeyPairFactory;
import tech.pegasys.teku.validator.api.InteropConfig;
import tech.pegasys.teku.validator.client.restapi.apis.schema.DeleteKeyResult;

public class MockStartValidatorSource implements ValidatorSource {
  private static final Logger LOG = LogManager.getLogger();
  private final Spec spec;
  private final InteropConfig config;
  private final AsyncRunner asyncRunner;

  public MockStartValidatorSource(
      final Spec spec, final InteropConfig config, final AsyncRunner asyncRunner) {
    this.spec = spec;
    this.config = config;
    this.asyncRunner = asyncRunner;
  }

  @Override
  public List<ValidatorProvider> getAvailableValidators() {
    final int startIndex = config.getInteropOwnedValidatorStartIndex();
    final int endIndex = startIndex + config.getInteropOwnedValidatorCount();
    LOG.log(Level.DEBUG, "Owning validator range " + startIndex + " to " + endIndex);
    return new MockStartValidatorKeyPairFactory()
        .generateKeyPairs(startIndex, endIndex).stream()
            .map(MockStartValidatorProvider::new)
            .collect(toList());
  }

  @Override
  public boolean canUpdateValidators() {
    return false;
  }

  @Override
  public DeleteKeyResult deleteValidator(final BLSPublicKey publicKey) {
    throw new UnsupportedOperationException("Cannot delete validator from mock validator source.");
  }

  @Override
  public AddLocalValidatorResult addValidator(
      final KeyStoreData keyStoreData, final String password, final BLSPublicKey publicKey) {
    throw new UnsupportedOperationException();
  }

  private class MockStartValidatorProvider implements ValidatorProvider {
    private final BLSKeyPair keyPair;

    private MockStartValidatorProvider(final BLSKeyPair keyPair) {
      this.keyPair = keyPair;
    }

    @Override
    public BLSPublicKey getPublicKey() {
      return keyPair.getPublicKey();
    }

    @Override
    public boolean isReadOnly() {
      return true;
    }

    @Override
    public Signer createSigner() {
      return new LocalSigner(spec, keyPair, asyncRunner);
    }
  }
}
