/*
 * Copyright Consensys Software Inc., 2022
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

public class DelegatingSpecConfigElectra extends DelegatingSpecConfigCapella
    implements SpecConfigElectra {
  private final SpecConfigElectra specConfigElectra;

  public DelegatingSpecConfigElectra(final SpecConfigElectra specConfig) {
    super(specConfig);
    this.specConfigElectra = SpecConfigElectra.required(specConfig);
  }

  @Override
  public Optional<SpecConfigElectra> toVersionElectra() {
    return Optional.of(this);
  }

  @Override
  public Bytes4 getElectraForkVersion() {
    return specConfigElectra.getElectraForkVersion();
  }

  @Override
  public UInt64 getElectraForkEpoch() {
    return specConfigElectra.getElectraForkEpoch();
  }

  @Override
  public int getMaxStems() {
    return specConfigElectra.getMaxStems();
  }

  @Override
  public int getMaxCommitmentsPerStem() {
    return specConfigElectra.getMaxCommitmentsPerStem();
  }

  @Override
  public int getVerkleWidth() {
    return specConfigElectra.getVerkleWidth();
  }

  @Override
  public int getIpaProofDepth() {
    return specConfigElectra.getIpaProofDepth();
  }
}
