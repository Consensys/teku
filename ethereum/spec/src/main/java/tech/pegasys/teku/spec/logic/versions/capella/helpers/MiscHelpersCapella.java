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

package tech.pegasys.teku.spec.logic.versions.capella.helpers;

import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigCapella;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.versions.bellatrix.helpers.MiscHelpersBellatrix;

public class MiscHelpersCapella extends MiscHelpersBellatrix {
  private final SpecConfigCapella specConfigCapella;

  public MiscHelpersCapella(final SpecConfigCapella specConfig) {
    super(specConfig);
    this.specConfigCapella = SpecConfigCapella.required(specConfig);
  }

  @Override
  public Bytes4 computeForkVersion(final UInt64 epoch) {
    specConfigCapella
        .nextForkEpoch()
        .ifPresent(
            nextForkEpoch -> {
              if (nextForkEpoch.isLessThanOrEqualTo(epoch)) {
                throw new IllegalArgumentException(
                    "Epoch " + epoch + " is post-capella, but expected capella at the latest");
              }
            });
    if (epoch.isGreaterThanOrEqualTo(specConfigCapella.getCapellaForkEpoch())) {
      return specConfigCapella.getCapellaForkVersion();
    }
    return super.computeForkVersion(epoch);
  }

  @Override
  public boolean isMergeTransitionComplete(final BeaconState genericState) {
    return true;
  }

  @Override
  public boolean isExecutionEnabled(final BeaconState genericState, final BeaconBlock block) {
    return true;
  }
}
