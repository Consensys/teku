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

import java.util.Optional;
import java.util.function.BiConsumer;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigCapella;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.config.SpecConfigDenebImpl;

public class DenebBuilder implements ForkConfigBuilder<SpecConfigCapella, SpecConfigDeneb> {

  private Bytes4 denebForkVersion;
  private UInt64 denebForkEpoch;

  private int fieldElementsPerBlob;
  private int maxBlobsPerBlock;

  private Optional<String> trustedSetupPath = Optional.empty();
  private boolean kzgNoop = false;

  DenebBuilder() {}

  @Override
  public SpecConfigDeneb build(final SpecConfigCapella specConfig) {
    return new SpecConfigDenebImpl(
        specConfig,
        denebForkVersion,
        denebForkEpoch,
        fieldElementsPerBlob,
        maxBlobsPerBlock,
        trustedSetupPath,
        kzgNoop);
  }

  public DenebBuilder denebForkEpoch(final UInt64 denebForkEpoch) {
    checkNotNull(denebForkEpoch);
    this.denebForkEpoch = denebForkEpoch;
    return this;
  }

  public DenebBuilder denebForkVersion(final Bytes4 denebForkVersion) {
    checkNotNull(denebForkVersion);
    this.denebForkVersion = denebForkVersion;
    return this;
  }

  public DenebBuilder fieldElementsPerBlob(final int fieldElementsPerBlob) {
    this.fieldElementsPerBlob = fieldElementsPerBlob;
    return this;
  }

  public DenebBuilder maxBlobsPerBlock(final int maxBlobsPerBlock) {
    this.maxBlobsPerBlock = maxBlobsPerBlock;
    return this;
  }

  public DenebBuilder trustedSetupPath(final String trustedSetupPath) {
    this.trustedSetupPath = Optional.of(trustedSetupPath);
    return this;
  }

  public DenebBuilder kzgNoop(final boolean kzgNoop) {
    this.kzgNoop = kzgNoop;
    return this;
  }

  @Override
  public void validate() {
    if (denebForkEpoch == null) {
      denebForkEpoch = SpecConfig.FAR_FUTURE_EPOCH;
      denebForkVersion = SpecBuilderUtil.PLACEHOLDER_FORK_VERSION;
    }
    SpecBuilderUtil.validateConstant("denebForkEpoch", denebForkEpoch);
    SpecBuilderUtil.validateConstant("denebForkVersion", denebForkVersion);
    SpecBuilderUtil.validateConstant("fieldElementsPerBlob", fieldElementsPerBlob);
    SpecBuilderUtil.validateConstant("maxBlobsPerBlock", maxBlobsPerBlock);
    if (!denebForkEpoch.equals(SpecConfig.FAR_FUTURE_EPOCH) && !kzgNoop) {
      SpecBuilderUtil.validateRequiredOptional("trustedSetupPath", trustedSetupPath);
    }
  }

  @Override
  public void addOverridableItemsToRawConfig(final BiConsumer<String, Object> rawConfig) {
    rawConfig.accept("DENEB_FORK_EPOCH", denebForkEpoch);
  }
}
