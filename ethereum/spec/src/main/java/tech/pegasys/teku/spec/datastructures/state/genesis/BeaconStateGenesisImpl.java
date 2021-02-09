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

package tech.pegasys.teku.spec.datastructures.state.genesis;

import java.util.function.Consumer;
import java.util.function.Function;
import tech.pegasys.teku.datastructures.state.PendingAttestation;
import tech.pegasys.teku.spec.datastructures.state.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.BeaconStateFields;
import tech.pegasys.teku.spec.datastructures.state.MutableBeaconState;
import tech.pegasys.teku.ssz.SSZTypes.SSZBackingList;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;
import tech.pegasys.teku.ssz.backing.SszData;
import tech.pegasys.teku.ssz.backing.cache.IntCache;
import tech.pegasys.teku.ssz.backing.schema.SszContainerSchema;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.view.SszContainerImpl;

class BeaconStateGenesisImpl extends SszContainerImpl implements BeaconStateGenesis {

  public BeaconStateGenesisImpl(SszContainerSchema<BeaconStateGenesis> schema) {
    super(schema);
  }

  BeaconStateGenesisImpl(
      SszContainerSchema<BeaconStateGenesis> type, TreeNode backingNode, IntCache<SszData> cache) {
    super(type, backingNode, cache);
  }

  BeaconStateGenesisImpl(SszContainerSchema<BeaconStateGenesis> type, TreeNode backingNode) {
    super(type, backingNode);
  }

  @Override
  public SSZList<PendingAttestation> getPrevious_epoch_attestations() {
    final int fieldIndex =
        getSchema().getFieldIndex(BeaconStateFields.PREVIOUS_EPOCH_ATTESTATIONS.name());
    return new SSZBackingList<>(
        PendingAttestation.class, getAny(fieldIndex), Function.identity(), Function.identity());
  }

  @Override
  public SSZList<PendingAttestation> getCurrent_epoch_attestations() {
    final int fieldIndex =
        getSchema().getFieldIndex(BeaconStateFields.CURRENT_EPOCH_ATTESTATIONS.name());
    return new SSZBackingList<>(
        PendingAttestation.class, getAny(fieldIndex), Function.identity(), Function.identity());
  }

  @Override
  public BeaconStateGenesis updatedGenesis(final Consumer<MutableBeaconStateGenesis> updater) {
    // TODO
    return null;
  }

  @Override
  public BeaconState updated(final Consumer<MutableBeaconState> updater) {
    // TODO
    return null;
  }
}
