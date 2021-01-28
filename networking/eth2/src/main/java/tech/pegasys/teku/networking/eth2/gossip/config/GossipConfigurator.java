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

package tech.pegasys.teku.networking.eth2.gossip.config;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.p2p.gossip.config.GossipConfig;
import tech.pegasys.teku.spec.constants.SpecConstants;
import tech.pegasys.teku.ssz.SSZTypes.Bytes4;

public interface GossipConfigurator {

  void configure(final GossipConfig.Builder gossipParams);

  void updateState(final Eth2State eth2State);

  static Builder builder() {
    return new Builder();
  }

  class Builder {
    private Boolean isScoringEnabled = false;
    private final Eth2State.Builder eth2State = Eth2State.builder();
    private SpecConstants specConstants;

    public GossipConfigurator build() {
      validate();
      return isScoringEnabled
          ? new GossipScoringConfigurator(specConstants, eth2State.build())
          : new DisabledScoringGossipConfigurator();
    }

    private void validate() {
      if (!isScoringEnabled) {
        // Disabled so no need to validate
        return;
      }
      checkNotNull(specConstants);
    }

    public Builder isScoringEnabled(final Boolean enableScoring) {
      checkNotNull(enableScoring);
      this.isScoringEnabled = enableScoring;
      return this;
    }

    public Builder specConstants(final SpecConstants specConstants) {
      checkNotNull(specConstants);
      this.specConstants = specConstants;
      return this;
    }

    public Builder validatorCount(final Integer validatorCount) {
      checkNotNull(validatorCount);
      eth2State.activeValidatorCount(validatorCount);
      return this;
    }

    public Builder gossipEncoding(final GossipEncoding gossipEncoding) {
      checkNotNull(gossipEncoding);
      eth2State.gossipEncoding(gossipEncoding);
      return this;
    }

    public Builder currentSlot(final UInt64 currentSlot) {
      checkNotNull(currentSlot);
      eth2State.currentSlot(currentSlot);
      return this;
    }

    public Builder forkDigest(final Optional<Bytes4> forkDigest) {
      checkNotNull(forkDigest);
      eth2State.forkDigest(forkDigest);
      return this;
    }
  }
}
