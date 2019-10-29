/*
 * Copyright 2019 ConsenSys AG.
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

package org.ethereum.beacon.consensus.transition;

import com.google.common.base.Objects;
import java.util.Map;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.ethereum.beacon.core.BeaconBlockHeader;
import org.ethereum.beacon.core.BeaconState;
import org.ethereum.beacon.core.MutableBeaconState;
import org.ethereum.beacon.core.operations.attestation.Crosslink;
import org.ethereum.beacon.core.spec.SpecConstants;
import org.ethereum.beacon.core.state.Checkpoint;
import org.ethereum.beacon.core.state.Eth1Data;
import org.ethereum.beacon.core.state.Fork;
import org.ethereum.beacon.core.state.PendingAttestation;
import org.ethereum.beacon.core.state.ValidatorRecord;
import org.ethereum.beacon.core.types.EpochNumber;
import org.ethereum.beacon.core.types.Gwei;
import org.ethereum.beacon.core.types.ShardNumber;
import org.ethereum.beacon.core.types.SlotNumber;
import org.ethereum.beacon.core.types.Time;
import org.ethereum.beacon.core.types.ValidatorIndex;
import org.ethereum.beacon.ssz.incremental.UpdateListener;
import tech.pegasys.artemis.ethereum.core.Hash32;
import tech.pegasys.artemis.util.collections.Bitvector;
import tech.pegasys.artemis.util.collections.ReadList;
import tech.pegasys.artemis.util.collections.ReadVector;
import tech.pegasys.artemis.util.uint.UInt64;

public class DelegateBeaconState implements BeaconState {
  private final BeaconState delegate;

  public DelegateBeaconState(BeaconState delegate) {
    this.delegate = delegate;
  }

  public BeaconState getDelegate() {
    return delegate;
  }

  @Override
  public UpdateListener getUpdateListener(
      String observerId, Supplier<UpdateListener> listenerFactory) {
    return delegate.getUpdateListener(observerId, listenerFactory);
  }

  @Override
  public Map<String, UpdateListener> getAllUpdateListeners() {
    return delegate.getAllUpdateListeners();
  }

  @Override
  public SlotNumber getSlot() {
    return delegate.getSlot();
  }

  @Override
  public Time getGenesisTime() {
    return delegate.getGenesisTime();
  }

  @Override
  public Fork getFork() {
    return delegate.getFork();
  }

  @Override
  public ReadList<ValidatorIndex, ValidatorRecord> getValidators() {
    return delegate.getValidators();
  }

  @Override
  public ReadList<ValidatorIndex, Gwei> getBalances() {
    return delegate.getBalances();
  }

  @Override
  public ReadVector<EpochNumber, Hash32> getRandaoMixes() {
    return delegate.getRandaoMixes();
  }

  @Override
  public ShardNumber getStartShard() {
    return delegate.getStartShard();
  }

  @Override
  public ReadList<Integer, PendingAttestation> getPreviousEpochAttestations() {
    return delegate.getPreviousEpochAttestations();
  }

  @Override
  public ReadList<Integer, PendingAttestation> getCurrentEpochAttestations() {
    return delegate.getCurrentEpochAttestations();
  }

  @Override
  public ReadVector<ShardNumber, Crosslink> getPreviousCrosslinks() {
    return delegate.getPreviousCrosslinks();
  }

  @Override
  public ReadVector<ShardNumber, Crosslink> getCurrentCrosslinks() {
    return delegate.getCurrentCrosslinks();
  }

  @Override
  public ReadVector<SlotNumber, Hash32> getBlockRoots() {
    return delegate.getBlockRoots();
  }

  @Override
  public ReadVector<SlotNumber, Hash32> getStateRoots() {
    return delegate.getStateRoots();
  }

  @Override
  public ReadVector<EpochNumber, Hash32> getActiveIndexRoots() {
    return delegate.getActiveIndexRoots();
  }

  @Override
  public ReadVector<EpochNumber, Gwei> getSlashings() {
    return delegate.getSlashings();
  }

  @Override
  public BeaconBlockHeader getLatestBlockHeader() {
    return delegate.getLatestBlockHeader();
  }

  @Override
  public ReadList<Integer, Hash32> getHistoricalRoots() {
    return delegate.getHistoricalRoots();
  }

  @Override
  public Eth1Data getEth1Data() {
    return delegate.getEth1Data();
  }

  @Override
  public ReadList<Integer, Eth1Data> getEth1DataVotes() {
    return delegate.getEth1DataVotes();
  }

  @Override
  public UInt64 getEth1DepositIndex() {
    return delegate.getEth1DepositIndex();
  }

  @Override
  public ReadVector<EpochNumber, Hash32> getCompactCommitteesRoots() {
    return delegate.getCompactCommitteesRoots();
  }

  @Override
  public Bitvector getJustificationBits() {
    return delegate.getJustificationBits();
  }

  @Override
  public Checkpoint getPreviousJustifiedCheckpoint() {
    return delegate.getPreviousJustifiedCheckpoint();
  }

  @Override
  public Checkpoint getCurrentJustifiedCheckpoint() {
    return delegate.getCurrentJustifiedCheckpoint();
  }

  @Override
  public Checkpoint getFinalizedCheckpoint() {
    return delegate.getFinalizedCheckpoint();
  }

  @Override
  public MutableBeaconState createMutableCopy() {
    return delegate.createMutableCopy();
  }

  @Override
  public String toStringShort(@Nullable SpecConstants constants) {
    return delegate.toStringShort(constants);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    DelegateBeaconState that = (DelegateBeaconState) o;
    return Objects.equal(delegate, that.delegate);
  }
}
