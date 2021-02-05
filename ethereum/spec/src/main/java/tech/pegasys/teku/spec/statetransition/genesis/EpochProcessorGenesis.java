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

package tech.pegasys.teku.spec.statetransition.genesis;

import tech.pegasys.teku.spec.datastructures.state.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.genesis.BeaconStateGenesis;
import tech.pegasys.teku.spec.datastructures.state.genesis.MutableBeaconStateGenesis;
import tech.pegasys.teku.spec.statetransition.EpochProcessor;

public class EpochProcessorGenesis implements EpochProcessor {

  @Override
  public BeaconState processEpoch(final BeaconState preState) {
    final BeaconStateGenesis versionedPreState =
        preState
            .toGenesisVersion()
            .orElseThrow(() -> new IllegalArgumentException("Unexpected BeaconState version"));
    return processEpoch(versionedPreState);
  }

  protected BeaconState processEpoch(final BeaconStateGenesis preState) {
    return preState.updatedGenesis(
        state -> {
          // TODO other methods etc
          processRewardsAndPenalties(state);
          processFinalUpdates(state);
        });
  }

  protected void processRewardsAndPenalties(final MutableBeaconStateGenesis beaconState) {
    // TODO
  }

  // Generic processor (not yet fork-specific)
  protected void processFinalUpdates(MutableBeaconState state) {
    // TODO
  }
}
