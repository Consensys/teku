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

package org.ethereum.beacon.core;

import org.ethereum.beacon.core.operations.attestation.Crosslink;
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
import tech.pegasys.artemis.ethereum.core.Hash32;
import tech.pegasys.artemis.util.collections.Bitvector;
import tech.pegasys.artemis.util.collections.WriteList;
import tech.pegasys.artemis.util.collections.WriteVector;
import tech.pegasys.artemis.util.uint.UInt64;

public interface MutableBeaconState extends BeaconState {

  void setSlot(SlotNumber slotNumber);

  void setGenesisTime(Time genesisTime);

  void setFork(Fork fork);

  @Override
  WriteList<ValidatorIndex, ValidatorRecord> getValidators();

  @Override
  WriteList<ValidatorIndex, Gwei> getBalances();

  @Override
  WriteVector<EpochNumber, Hash32> getRandaoMixes();

  void setStartShard(ShardNumber startShard);

  void setJustificationBits(Bitvector justificationBits);

  void setLatestBlockHeader(BeaconBlockHeader latestBlockHeader);

  void setPreviousJustifiedCheckpoint(Checkpoint previousJustifiedCheckpoint);

  void setCurrentJustifiedCheckpoint(Checkpoint currentJustifiedCheckpoint);

  void setFinalizedCheckpoint(Checkpoint finalizedCheckpoint);

  @Override
  Bitvector getJustificationBits();

  @Override
  WriteVector<ShardNumber, Crosslink> getPreviousCrosslinks();

  @Override
  WriteVector<ShardNumber, Crosslink> getCurrentCrosslinks();

  @Override
  WriteVector<SlotNumber, Hash32> getBlockRoots();

  @Override
  WriteVector<SlotNumber, Hash32> getStateRoots();

  @Override
  WriteVector<EpochNumber, Hash32> getActiveIndexRoots();

  @Override
  WriteVector<EpochNumber, Hash32> getCompactCommitteesRoots();

  @Override
  WriteVector<EpochNumber, Gwei> getSlashings();

  @Override
  WriteList<Integer, PendingAttestation> getPreviousEpochAttestations();

  @Override
  WriteList<Integer, PendingAttestation> getCurrentEpochAttestations();

  @Override
  WriteList<Integer, Hash32> getHistoricalRoots();

  void setEth1Data(Eth1Data latestEth1Data);

  @Override
  WriteList<Integer, Eth1Data> getEth1DataVotes();

  void setEth1DataVotes(WriteList<Integer, Eth1Data> eth1DataVotes);

  void setEth1DepositIndex(UInt64 depositIndex);

  BeaconState createImmutable();
}
