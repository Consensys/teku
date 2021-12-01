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
import org.apache.commons.lang3.NotImplementedException;
import tech.pegasys.signers.bls.keystore.model.KeyStoreData;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.core.signatures.DeletableSigner;
import tech.pegasys.teku.core.signatures.Signer;
import tech.pegasys.teku.core.signatures.SlashingProtector;

public class MutableLocalValidatorSource extends SlashingProtectedValidatorSource {

  MutableLocalValidatorSource(
      final LocalValidatorSource delegate, final SlashingProtector slashingProtector) {
    super(delegate, slashingProtector);
  }

  @Override
  public boolean canAddValidator() {
    return true;
  }

  @Override
  public MutableValidatorAddResult addValidator(
      final KeyStoreData keyStoreData, final String password) {
    throw new NotImplementedException();
  }

  @Override
  public List<ValidatorProvider> getAvailableValidators() {
    return super.getAvailableValidators().stream()
        .map(MutableLocalValidatorProvider::new)
        .collect(toList());
  }

  private static class MutableLocalValidatorProvider implements ValidatorProvider {
    private final ValidatorProvider delegate;

    private MutableLocalValidatorProvider(final ValidatorProvider delegate) {
      this.delegate = delegate;
    }

    @Override
    public BLSPublicKey getPublicKey() {
      return delegate.getPublicKey();
    }

    @Override
    public boolean isReadOnly() {
      return delegate.isReadOnly();
    }

    @Override
    public Signer createSigner() {
      return new DeletableSigner(delegate.createSigner());
    }
  }
}
