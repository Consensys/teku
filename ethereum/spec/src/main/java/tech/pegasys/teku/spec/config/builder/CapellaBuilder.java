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
import tech.pegasys.teku.spec.config.SpecConfigBellatrix;
import tech.pegasys.teku.spec.config.SpecConfigCapella;
import tech.pegasys.teku.spec.config.SpecConfigCapellaImpl;

public class CapellaBuilder implements ForkConfigBuilder<SpecConfigBellatrix, SpecConfigCapella> {

  private Bytes4 capellaForkVersion;
  private UInt64 capellaForkEpoch;

  private UInt64 maxBlsToExecutionChanges;
  private int maxWithdrawalsPerPayload;

  CapellaBuilder() {}

  @Override
  public SpecConfigCapella build(final SpecConfigBellatrix specConfig) {
    return new SpecConfigCapellaImpl(
        specConfig,
        capellaForkVersion,
        capellaForkEpoch,
        maxBlsToExecutionChanges,
        maxWithdrawalsPerPayload);
  }

  public CapellaBuilder capellaForkEpoch(final UInt64 capellaForkEpoch) {
    checkNotNull(capellaForkEpoch);
    this.capellaForkEpoch = capellaForkEpoch;
    return this;
  }

  public CapellaBuilder capellaForkVersion(final Bytes4 capellaForkVersion) {
    checkNotNull(capellaForkVersion);
    this.capellaForkVersion = capellaForkVersion;
    return this;
  }

  public CapellaBuilder maxBlsToExecutionChanges(final UInt64 maxBlsToExecutionChanges) {
    this.maxBlsToExecutionChanges = maxBlsToExecutionChanges;
    return this;
  }

  public CapellaBuilder maxWithdrawalsPerPayload(final int maxWithdrawalsPerPayload) {
    this.maxWithdrawalsPerPayload = maxWithdrawalsPerPayload;
    return this;
  }

  @Override
  public void validate() {
    if (capellaForkEpoch == null) {
      // Config doesn't include Capella configuration but we need some values for the REST API
      // type definitions.
      // Provide MainNet-like defaults and ensure Capella isn't actually supported
      capellaForkEpoch = SpecConfig.FAR_FUTURE_EPOCH;
      capellaForkVersion = SpecBuilderUtil.PLACEHOLDER_FORK_VERSION;
    }
    SpecBuilderUtil.validateConstant("capellaForkVersion", capellaForkVersion);
    SpecBuilderUtil.validateConstant("capellaForkEpoch", capellaForkEpoch);
    SpecBuilderUtil.validateConstant("maxBlsToExecutionChanges", maxBlsToExecutionChanges);
    SpecBuilderUtil.validateConstant("maxWithdrawalsPerPayload", maxWithdrawalsPerPayload);
  }

  @Override
  public void addOverridableItemsToRawConfig(final BiConsumer<String, Object> rawConfig) {
    rawConfig.accept("CAPELLA_FORK_EPOCH", capellaForkEpoch);
  }
}
