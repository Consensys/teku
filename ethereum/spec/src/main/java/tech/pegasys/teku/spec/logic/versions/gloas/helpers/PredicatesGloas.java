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

package tech.pegasys.teku.spec.logic.versions.gloas.helpers;

import java.util.Optional;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.PredicatesElectra;

public class PredicatesGloas extends PredicatesElectra {

  public static PredicatesGloas required(final Predicates predicates) {
    return predicates
        .toVersionGloas()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected Gloas predicates but got " + predicates.getClass().getSimpleName()));
  }

  public PredicatesGloas(final SpecConfig specConfig) {
    super(specConfig);
  }

  public boolean isParentBlockFull(final BeaconState state) {
    return BeaconStateGloas.required(state)
        .getLatestExecutionPayloadHeader()
        .getBlockHash()
        .equals(BeaconStateGloas.required(state).getLatestBlockHash());
  }

  @Override
  public Optional<PredicatesGloas> toVersionGloas() {
    return Optional.of(this);
  }
}
