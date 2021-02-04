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

package tech.pegasys.teku.spec.containers.state.genesis;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.function.Consumer;
import tech.pegasys.teku.datastructures.state.PendingAttestation;
import tech.pegasys.teku.spec.containers.state.BeaconState;
import tech.pegasys.teku.spec.containers.state.DelegatingBeaconState;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;

class DelegatingBeaconStateGenesis extends DelegatingBeaconState implements BeaconStateGenesis {

  public DelegatingBeaconStateGenesis(final BeaconState state) {
    super(state);
    validate();
  }

  private void validate() {
    checkArgument(
        state.maybeGetPrevious_epoch_attestations().isPresent(),
        "State must contain a previousEpochAttestations field");
    checkArgument(
        state.maybeGetPrevious_epoch_attestations().isPresent(),
        "State must contain a currentEpochAttestations field");
  }

  @Override
  public SSZList<PendingAttestation> getPrevious_epoch_attestations() {
    return state.maybeGetPrevious_epoch_attestations().orElseThrow();
  }

  @Override
  public SSZList<PendingAttestation> getCurrent_epoch_attestations() {
    return state.maybeGetCurrent_epoch_attestations().orElseThrow();
  }

  @Override
  public BeaconState updateGenesis(final Consumer<MutableBeaconStateGenesis> updater) {
    return state.update(
        mutableState -> {
          final DelegatingMutableBeaconStateGenesis genesisState =
              new DelegatingMutableBeaconStateGenesis(mutableState);
          updater.accept(genesisState);
        });
  }
}
