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
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.core.signatures.Signer;
import tech.pegasys.teku.core.signatures.SlashingProtectedSigner;
import tech.pegasys.teku.core.signatures.SlashingProtector;

public class SlashingProtectedValidatorSource implements ValidatorSource {
  private final ValidatorSource delegate;
  private final SlashingProtector slashingProtector;

  public SlashingProtectedValidatorSource(
      final ValidatorSource delegate, final SlashingProtector slashingProtector) {
    this.delegate = delegate;
    this.slashingProtector = slashingProtector;
  }

  @Override
  public List<ValidatorProvider> getAvailableValidators() {
    return delegate.getAvailableValidators().stream()
        .map(SlashingProtectedValidatorProvider::new)
        .collect(toList());
  }

  private class SlashingProtectedValidatorProvider implements ValidatorProvider {
    private final ValidatorProvider delegate;

    private SlashingProtectedValidatorProvider(final ValidatorProvider delegate) {
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
      // TODO: Consider caching these to guarantee we can't possible use different
      // `SlashingProtectedSigner` instances with the same key
      return new SlashingProtectedSigner(
          getPublicKey(), slashingProtector, delegate.createSigner());
    }
  }
}
