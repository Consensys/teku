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
import tech.pegasys.teku.spec.SpecMilestone;

public class SpecConfigEip7805Impl extends DelegatingSpecConfigFulu implements SpecConfigEip7805 {

  private final Bytes4 eip7805ForkVersion;
  private final UInt64 eip7805ForkEpoch;
  private final int ilCommitteeSize;
  private final int maxTransactionsPerInclusionList;
  private final int maxRequestInclusionList;
  private final int maxBytesPerInclusionList;

  public SpecConfigEip7805Impl(
      final SpecConfigFulu specConfig,
      final Bytes4 eip7805ForkVersion,
      final UInt64 eip7805ForkEpoch,
      final int ilCommitteeSize,
      final int maxTransactionsPerInclusionList,
      final int maxRequestInclusionList,
      final int maxBytesPerInclusionList) {
    super(specConfig);
    this.eip7805ForkVersion = eip7805ForkVersion;
    this.eip7805ForkEpoch = eip7805ForkEpoch;
    this.ilCommitteeSize = ilCommitteeSize;
    this.maxTransactionsPerInclusionList = maxTransactionsPerInclusionList;
    this.maxRequestInclusionList = maxRequestInclusionList;
    this.maxBytesPerInclusionList = maxBytesPerInclusionList;
  }

  @Override
  public Bytes4 getEip7805ForkVersion() {
    return eip7805ForkVersion;
  }

  @Override
  public UInt64 getEip7805ForkEpoch() {
    return eip7805ForkEpoch;
  }

  @Override
  public int getIlCommitteeSize() {
    return ilCommitteeSize;
  }

  @Override
  public int getMaxTransactionsPerInclusionList() {
    return maxTransactionsPerInclusionList;
  }

  @Override
  public int getMaxRequestInclusionList() {
    return maxRequestInclusionList;
  }

  @Override
  public int getMaxBytesPerInclusionList() {
    return maxBytesPerInclusionList;
  }

  @Override
  public Optional<SpecConfigEip7805> toVersionEip7805() {
    return Optional.of(this);
  }

  @Override
  public SpecMilestone getMilestone() {
    return SpecMilestone.EIP7805;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final SpecConfigEip7805Impl that = (SpecConfigEip7805Impl) o;
    return Objects.equals(specConfig, that.specConfig)
        && Objects.equals(eip7805ForkVersion, that.eip7805ForkVersion)
        && Objects.equals(eip7805ForkEpoch, that.eip7805ForkEpoch)
        && ilCommitteeSize == that.ilCommitteeSize
        && maxTransactionsPerInclusionList == that.maxTransactionsPerInclusionList
        && maxRequestInclusionList == that.maxRequestInclusionList
        && maxBytesPerInclusionList == that.maxBytesPerInclusionList;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        specConfig,
        eip7805ForkVersion,
        eip7805ForkEpoch,
        ilCommitteeSize,
        maxTransactionsPerInclusionList,
        maxRequestInclusionList,
        maxBytesPerInclusionList);
  }
}
