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

import java.util.function.Consumer;
import tech.pegasys.teku.datastructures.state.PendingAttestation;
import tech.pegasys.teku.spec.containers.state.BeaconState;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;

public interface BeaconStateGenesis extends BeaconState {

  static BeaconStateGenesis toGenesisState(BeaconState state) {
    return new DelegatingBeaconStateGenesis(state);
  }

  SSZList<PendingAttestation> getPrevious_epoch_attestations();

  SSZList<PendingAttestation> getCurrent_epoch_attestations();

  BeaconState updateGenesis(final Consumer<MutableBeaconStateGenesis> updater);
}
