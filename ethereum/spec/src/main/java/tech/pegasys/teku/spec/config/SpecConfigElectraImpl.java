/*
 * Copyright Consensys Software Inc., 2024
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

import java.util.Objects;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class SpecConfigElectraImpl extends DelegatingSpecConfigDeneb implements SpecConfigElectra {

  private final Bytes4 electraForkVersion;
  private final UInt64 electraForkEpoch;

  private final int maxDepositReceiptsPerPayload;
  private final int maxTransactionPerInclusionList;

  public SpecConfigElectraImpl(
      final SpecConfigDeneb specConfig,
      final Bytes4 electraForkVersion,
      final UInt64 electraForkEpoch,
      final int maxDepositReceiptsPerPayload,
      final int maxTransactionPerInclusionList) {
    super(specConfig);
    this.electraForkVersion = electraForkVersion;
    this.electraForkEpoch = electraForkEpoch;
    this.maxDepositReceiptsPerPayload = maxDepositReceiptsPerPayload;
    this.maxTransactionPerInclusionList = maxTransactionPerInclusionList;
  }

  @Override
  public Bytes4 getElectraForkVersion() {
    return electraForkVersion;
  }

  @Override
  public UInt64 getElectraForkEpoch() {
    return electraForkEpoch;
  }

  @Override
  public int getMaxDepositReceiptsPerPayload() {
    return maxDepositReceiptsPerPayload;
  }

  @Override
  public int getMaxTransactionPerInclusionList() {
    return maxTransactionPerInclusionList;
  }

  @Override
  public Optional<SpecConfigElectra> toVersionElectra() {
    return Optional.of(this);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final SpecConfigElectraImpl that = (SpecConfigElectraImpl) o;
    return Objects.equals(specConfig, that.specConfig)
        && Objects.equals(electraForkVersion, that.electraForkVersion)
        && Objects.equals(electraForkEpoch, that.electraForkEpoch)
        && maxDepositReceiptsPerPayload == that.maxDepositReceiptsPerPayload;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        specConfig, electraForkVersion, electraForkEpoch, maxDepositReceiptsPerPayload);
  }
}
