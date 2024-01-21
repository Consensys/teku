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

package tech.pegasys.teku.spec.config.builder;

import static com.google.common.base.Preconditions.checkNotNull;
import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigCapella;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.config.SpecConfigElectraImpl;

public class ElectraBuilder implements ForkConfigBuilder<SpecConfigCapella, SpecConfigElectra> {

  private Bytes4 electraForkVersion;
  private UInt64 electraForkEpoch;

  private Integer maxStems;
  private Integer maxCommitmentsPerStem;
  private Integer verkleWidth;
  private Integer ipaProofDepth;

  ElectraBuilder() {}

  @Override
  public SpecConfigElectra build(final SpecConfigCapella specConfig) {
    return new SpecConfigElectraImpl(
        specConfig,
        electraForkVersion,
        electraForkEpoch,
        maxStems,
        maxCommitmentsPerStem,
        verkleWidth,
        ipaProofDepth);
  }

  public ElectraBuilder electraForkEpoch(final UInt64 electraForkEpoch) {
    checkNotNull(electraForkEpoch);
    this.electraForkEpoch = electraForkEpoch;
    return this;
  }

  public ElectraBuilder electraForkVersion(final Bytes4 electraForkVersion) {
    checkNotNull(electraForkVersion);
    this.electraForkVersion = electraForkVersion;
    return this;
  }

  public ElectraBuilder maxStems(final Integer maxStems) {
    this.maxStems = maxStems;
    return this;
  }

  public ElectraBuilder maxCommitmentsPerStem(final Integer maxCommitmentsPerStem) {
    this.maxCommitmentsPerStem = maxCommitmentsPerStem;
    return this;
  }

  public ElectraBuilder verkleWidth(final Integer verkleWidth) {
    this.verkleWidth = verkleWidth;
    return this;
  }

  public ElectraBuilder ipaProofDepth(final Integer ipaProofDepth) {
    this.ipaProofDepth = ipaProofDepth;
    return this;
  }

  public UInt64 getElectraForkEpoch() {
    return electraForkEpoch;
  }

  @Override
  public void validate() {
    if (electraForkEpoch == null) {
      // Config doesn't include Electra configuration but we need some values for the REST API
      // type definitions.
      // Provide MainNet-like defaults and ensure Electra isn't actually supported
      electraForkEpoch = SpecConfig.FAR_FUTURE_EPOCH;
      electraForkVersion = SpecBuilderUtil.PLACEHOLDER_FORK_VERSION;
    }

    // Fill default zeros if fork is unsupported
    if (electraForkEpoch.equals(FAR_FUTURE_EPOCH)) {
      SpecBuilderUtil.fillMissingValuesWithZeros(this);
    }

    validateConstants();
  }

  @Override
  public Map<String, Object> getValidationMap() {
    final Map<String, Object> constants = new HashMap<>();
    constants.put("capellaForkVersion", electraForkVersion);
    constants.put("capellaForkEpoch", electraForkEpoch);
    constants.put("maxStems", maxStems);
    constants.put("maxCommitmentsPerStem", maxCommitmentsPerStem);
    constants.put("verkleWidth", verkleWidth);
    constants.put("ipaProofDepth", ipaProofDepth);

    return constants;
  }

  @Override
  public void addOverridableItemsToRawConfig(final BiConsumer<String, Object> rawConfig) {
    rawConfig.accept("ELECTRA_FORK_EPOCH", electraForkEpoch);
  }
}
