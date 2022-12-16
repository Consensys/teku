/*
 * Copyright ConsenSys Software Inc., 2022
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
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class DelegatingSpecConfigCapella extends DelegatingSpecConfigBellatrix
    implements SpecConfigCapella {
  private final SpecConfigCapella specConfigCapella;

  public DelegatingSpecConfigCapella(final SpecConfigCapella specConfig) {
    super(specConfig);
    this.specConfigCapella = SpecConfigCapella.required(specConfig);
  }

  @Override
  public Optional<SpecConfigCapella> toVersionCapella() {
    return Optional.of(this);
  }

  @Override
  public Bytes4 getCapellaForkVersion() {
    return specConfigCapella.getCapellaForkVersion();
  }

  @Override
  public UInt64 getCapellaForkEpoch() {
    return specConfigCapella.getCapellaForkEpoch();
  }

  @Override
  public int getMaxWithdrawalsPerPayload() {
    return specConfigCapella.getMaxWithdrawalsPerPayload();
  }

  @Override
  public int getMaxValidatorsPerWithdrawalSweep() {
    return specConfigCapella.getMaxValidatorsPerWithdrawalSweep();
  }

  @Override
  public int getMaxBlsToExecutionChanges() {
    return specConfigCapella.getMaxBlsToExecutionChanges();
  }
}
