/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.genesis;

import com.google.common.base.MoreObjects.ToStringHelper;
import tech.pegasys.teku.spec.datastructures.state.PendingAttestation;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.AbstractMutableBeaconStateImpl;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.TransitionCaches;
import tech.pegasys.teku.ssz.SSZTypes.SSZMutableList;
import tech.pegasys.teku.ssz.backing.SszData;
import tech.pegasys.teku.ssz.backing.cache.IntCache;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;

class MutableBeaconStateGenesisImpl extends AbstractMutableBeaconStateImpl<BeaconStateGenesisImpl>
    implements MutableBeaconStateGenesis, BeaconStateCache {

  private SSZMutableList<PendingAttestation> previousEpochAttestations;
  private SSZMutableList<PendingAttestation> currentEpochAttestations;

  MutableBeaconStateGenesisImpl(BeaconStateGenesisImpl backingImmutableView) {
    super(backingImmutableView);
  }

  MutableBeaconStateGenesisImpl(BeaconStateGenesisImpl backingImmutableView, boolean builder) {
    super(backingImmutableView, builder);
  }

  @Override
  protected BeaconStateGenesisImpl createImmutableBeaconState(
      TreeNode backingNode, IntCache<SszData> viewCache, TransitionCaches transitionCache) {
    return new BeaconStateGenesisImpl(getSchema(), backingNode, viewCache, transitionCache);
  }

  @Override
  public BeaconStateGenesis commitChanges() {
    return (BeaconStateGenesis) super.commitChanges();
  }

  @Override
  public SSZMutableList<PendingAttestation> getPrevious_epoch_attestations() {
    return previousEpochAttestations != null
        ? previousEpochAttestations
        : (previousEpochAttestations =
            MutableBeaconStateGenesis.super.getPrevious_epoch_attestations());
  }

  @Override
  public SSZMutableList<PendingAttestation> getCurrent_epoch_attestations() {
    return currentEpochAttestations != null
        ? currentEpochAttestations
        : (currentEpochAttestations =
            MutableBeaconStateGenesis.super.getCurrent_epoch_attestations());
  }

  @Override
  public <E1 extends Exception, E2 extends Exception, E3 extends Exception> BeaconState updated(
      Mutator<MutableBeaconState, E1, E2, E3> mutator) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <E1 extends Exception, E2 extends Exception, E3 extends Exception>
      BeaconStateGenesis updatedGenesis(Mutator<MutableBeaconStateGenesis, E1, E2, E3> mutator)
          throws E1, E2, E3 {
    throw new UnsupportedOperationException();
  }

  @Override
  protected void addCustomFields(ToStringHelper stringBuilder) {
    BeaconStateGenesisImpl.describeCustomFields(stringBuilder, this);
  }
}
