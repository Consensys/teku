/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.spec.config;

import java.util.Optional;

public class DelegatingSpecConfigGloas extends DelegatingSpecConfigFulu implements SpecConfigGloas {
  private final SpecConfigGloas delegate;

  public DelegatingSpecConfigGloas(final SpecConfigGloas specConfig) {
    super(specConfig);
    this.delegate = SpecConfigGloas.required(specConfig);
  }

  @Override
  public int getAggregateDueBps() {
    return delegate.getAggregateDueBps();
  }

  @Override
  public int getAttestationDueBps() {
    return delegate.getAttestationDueBps();
  }

  @Override
  public int getContributionDueBps() {
    return delegate.getContributionDueBps();
  }

  @Override
  public int getMaxRequestPayloads() {
    return delegate.getMaxRequestPayloads();
  }

  @Override
  public int getMinBuilderWithdrawabilityDelay() {
    return delegate.getMinBuilderWithdrawabilityDelay();
  }

  @Override
  public int getPayloadAttestationDueBps() {
    return delegate.getPayloadAttestationDueBps();
  }

  @Override
  public int getPtcSize() {
    return delegate.getPtcSize();
  }

  @Override
  public int getMaxPayloadAttestations() {
    return delegate.getMaxPayloadAttestations();
  }

  @Override
  public long getBuilderRegistryLimit() {
    return delegate.getBuilderRegistryLimit();
  }

  @Override
  public long getBuilderPendingWithdrawalsLimit() {
    return delegate.getBuilderPendingWithdrawalsLimit();
  }

  @Override
  public int getMaxBuildersPerWithdrawalsSweep() {
    return delegate.getMaxBuildersPerWithdrawalsSweep();
  }

  @Override
  public Optional<SpecConfigGloas> toVersionGloas() {
    return delegate.toVersionGloas();
  }

  @Override
  public int getSyncMessageDueBps() {
    return delegate.getSyncMessageDueBps();
  }
}
