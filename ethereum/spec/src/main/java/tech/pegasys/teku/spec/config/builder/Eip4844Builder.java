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

package tech.pegasys.teku.spec.config.builder;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.function.BiConsumer;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigCapella;
import tech.pegasys.teku.spec.config.SpecConfigEip4844;
import tech.pegasys.teku.spec.config.SpecConfigEip4844Impl;

public class Eip4844Builder implements ForkConfigBuilder<SpecConfigCapella, SpecConfigEip4844> {

  private Bytes4 eip4844ForkVersion;
  private UInt64 eip4844ForkEpoch;

  private int fieldElementsPerBlob;
  private int maxBlobsPerBlock;

  Eip4844Builder() {}

  @Override
  public SpecConfigEip4844 build(final SpecConfigCapella specConfig) {
    return new SpecConfigEip4844Impl(
        specConfig, eip4844ForkVersion, eip4844ForkEpoch, fieldElementsPerBlob, maxBlobsPerBlock);
  }

  public Eip4844Builder eip4844ForkEpoch(final UInt64 eip4844ForkEpoch) {
    checkNotNull(eip4844ForkEpoch);
    this.eip4844ForkEpoch = eip4844ForkEpoch;
    return this;
  }

  public Eip4844Builder eip4844ForkVersion(final Bytes4 eip4844ForkVersion) {
    checkNotNull(eip4844ForkVersion);
    this.eip4844ForkVersion = eip4844ForkVersion;
    return this;
  }

  public Eip4844Builder fieldElementsPerBlob(final int fieldElementsPerBlob) {
    this.fieldElementsPerBlob = fieldElementsPerBlob;
    return this;
  }

  public Eip4844Builder maxBlobsPerBlock(final int maxBlobsPerBlock) {
    this.maxBlobsPerBlock = maxBlobsPerBlock;
    return this;
  }

  @Override
  public void validate() {
    if (eip4844ForkEpoch == null) {
      eip4844ForkEpoch = SpecConfig.FAR_FUTURE_EPOCH;
      eip4844ForkVersion = SpecBuilderUtil.PLACEHOLDER_FORK_VERSION;
    }
    SpecBuilderUtil.validateConstant("eip4844ForkEpoch", eip4844ForkEpoch);
    SpecBuilderUtil.validateConstant("eip4844ForkVersion", eip4844ForkVersion);
    SpecBuilderUtil.validateConstant("fieldElementsPerBlob", fieldElementsPerBlob);
    SpecBuilderUtil.validateConstant("maxBlobsPerBlock", maxBlobsPerBlock);
  }

  @Override
  public void addOverridableItemsToRawConfig(final BiConsumer<String, Object> rawConfig) {
    rawConfig.accept("EIP4844_FORK_EPOCH", eip4844ForkEpoch);
  }
}
