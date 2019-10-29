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

package org.ethereum.beacon.core.state;

import java.util.Map;
import java.util.function.Supplier;
import org.ethereum.beacon.core.BeaconBlockHeader;
import org.ethereum.beacon.core.BeaconState;
import org.ethereum.beacon.core.MutableBeaconState;
import org.ethereum.beacon.core.operations.attestation.Crosslink;
import org.ethereum.beacon.core.spec.SpecConstants;
import org.ethereum.beacon.core.types.EpochNumber;
import org.ethereum.beacon.core.types.Gwei;
import org.ethereum.beacon.core.types.ShardNumber;
import org.ethereum.beacon.core.types.SlotNumber;
import org.ethereum.beacon.core.types.Time;
import org.ethereum.beacon.core.types.ValidatorIndex;
import org.ethereum.beacon.ssz.annotation.SSZSerializable;
import org.ethereum.beacon.ssz.incremental.ObservableCompositeHelper;
import org.ethereum.beacon.ssz.incremental.ObservableCompositeHelper.ObsValue;
import org.ethereum.beacon.ssz.incremental.ObservableListImpl;
import org.ethereum.beacon.ssz.incremental.UpdateListener;
import tech.pegasys.artemis.ethereum.core.Hash32;
import tech.pegasys.artemis.util.collections.Bitvector;
import tech.pegasys.artemis.util.collections.WriteList;
import tech.pegasys.artemis.util.uint.UInt64;

@SSZSerializable
public class BeaconStateImpl implements MutableBeaconState {

  private final ObservableCompositeHelper obsHelper = new ObservableCompositeHelper();
  private final SpecConstants specConstants; // backup

  /* Versioning */
  private final ObsValue<Time> genesisTime;
  private final ObsValue<SlotNumber> slot;
  private final ObsValue<Fork> fork;

  /* History */
  private final ObsValue<BeaconBlockHeader> latestBlockHeader;
  private final ObsValue<WriteList<SlotNumber, Hash32>> blockRoots;
  private final ObsValue<WriteList<SlotNumber, Hash32>> stateRoots;
  private final ObsValue<WriteList<Integer, Hash32>> historicalRoots;

  /* Eth1 */
  private final ObsValue<Eth1Data> eth1Data;
  private final ObsValue<WriteList<Integer, Eth1Data>> eth1DataVotes;
  private final ObsValue<UInt64> eth1DepositIndex;

  /* Registry */
  private final ObsValue<WriteList<ValidatorIndex, ValidatorRecord>> validators;
  private final ObsValue<WriteList<ValidatorIndex, Gwei>> balances;

  /* Shuffling */
  private final ObsValue<ShardNumber> startShard;
  private final ObsValue<WriteList<EpochNumber, Hash32>> randaoMixes;
  private final ObsValue<WriteList<EpochNumber, Hash32>> activeIndexRoots;
  private final ObsValue<WriteList<EpochNumber, Hash32>> compactCommitteesRoots;

  /* Slashings */
  private final ObsValue<WriteList<EpochNumber, Gwei>> slashings;

  /* Attestations */
  private final ObsValue<WriteList<Integer, PendingAttestation>> previousEpochAttestations;
  private final ObsValue<WriteList<Integer, PendingAttestation>> currentEpochAttestations;

  /* Crosslinks */
  private final ObsValue<WriteList<ShardNumber, Crosslink>> previousCrosslinks;
  private final ObsValue<WriteList<ShardNumber, Crosslink>> currentCrosslinks;

  /* Finality */
  private final ObsValue<Bitvector> justificationBits;
  private final ObsValue<Checkpoint> previousJustifiedCheckpoint;
  private final ObsValue<Checkpoint> currentJustifiedCheckpoint;
  private final ObsValue<Checkpoint> finalizedCheckpoint;

  public BeaconStateImpl(SpecConstants specConstants) {
    this.specConstants = specConstants;
    // Versioning
    this.genesisTime = obsHelper.newValue(Time.ZERO);
    this.slot = obsHelper.newValue(SlotNumber.ZERO);
    this.fork = obsHelper.newValue(Fork.EMPTY);

    // History
    this.latestBlockHeader = obsHelper.newValue(BeaconBlockHeader.EMPTY);
    this.blockRoots = obsHelper.newValue(ObservableListImpl.create(SlotNumber::of, true));
    this.stateRoots = obsHelper.newValue(ObservableListImpl.create(SlotNumber::of, true));
    this.historicalRoots =
        obsHelper.newValue(
            ObservableListImpl.create(
                Integer::valueOf, specConstants.getHistoricalRootsLimit().longValue()));

    // Eth1
    this.eth1Data = obsHelper.newValue(Eth1Data.EMPTY);
    this.eth1DataVotes =
        obsHelper.newValue(
            ObservableListImpl.create(
                Integer::valueOf, specConstants.getSlotsPerEth1VotingPeriod().longValue()));
    this.eth1DepositIndex = obsHelper.newValue(UInt64.ZERO);

    // Registry
    this.validators =
        obsHelper.newValue(
            ObservableListImpl.create(
                ValidatorIndex::of, specConstants.getValidatorRegistryLimit().longValue()));
    this.balances =
        obsHelper.newValue(
            ObservableListImpl.create(
                ValidatorIndex::of, specConstants.getValidatorRegistryLimit().longValue()));

    // Shuffling
    this.startShard = obsHelper.newValue(ShardNumber.ZERO);
    this.randaoMixes = obsHelper.newValue(ObservableListImpl.create(EpochNumber::of, true));
    this.activeIndexRoots = obsHelper.newValue(ObservableListImpl.create(EpochNumber::of, true));
    this.compactCommitteesRoots =
        obsHelper.newValue(ObservableListImpl.create(EpochNumber::of, true));

    // Slashings
    this.slashings = obsHelper.newValue(ObservableListImpl.create(EpochNumber::of, true));

    // Attestations
    this.previousEpochAttestations =
        obsHelper.newValue(
            ObservableListImpl.create(
                Integer::valueOf, specConstants.getMaxEpochAttestations().longValue()));
    this.currentEpochAttestations =
        obsHelper.newValue(
            ObservableListImpl.create(
                Integer::valueOf, specConstants.getMaxEpochAttestations().longValue()));

    // Crosslinks
    this.previousCrosslinks = obsHelper.newValue(ObservableListImpl.create(ShardNumber::of, true));
    this.currentCrosslinks = obsHelper.newValue(ObservableListImpl.create(ShardNumber::of, true));

    // Finality
    this.justificationBits = obsHelper.newValue(Bitvector.EMPTY);
    this.previousJustifiedCheckpoint = obsHelper.newValue(Checkpoint.EMPTY);
    this.currentJustifiedCheckpoint = obsHelper.newValue(Checkpoint.EMPTY);
    this.finalizedCheckpoint = obsHelper.newValue(Checkpoint.EMPTY);
  }

  private BeaconStateImpl(BeaconState state, SpecConstants specConstants) {
    this(specConstants);
    genesisTime.set(state.getGenesisTime());
    slot.set(state.getSlot());
    fork.set(state.getFork());

    latestBlockHeader.set(state.getLatestBlockHeader());
    blockRoots.set(state.getBlockRoots().createMutableCopy());
    stateRoots.set(state.getStateRoots().createMutableCopy());
    historicalRoots.set(state.getHistoricalRoots().createMutableCopy());

    eth1Data.set(state.getEth1Data());
    eth1DataVotes.set(state.getEth1DataVotes().createMutableCopy());
    eth1DepositIndex.set(state.getEth1DepositIndex());

    validators.set(state.getValidators().createMutableCopy());
    balances.set(state.getBalances().createMutableCopy());

    startShard.set(state.getStartShard());
    randaoMixes.set(state.getRandaoMixes().createMutableCopy());
    activeIndexRoots.set(state.getActiveIndexRoots().createMutableCopy());
    compactCommitteesRoots.set(state.getCompactCommitteesRoots().createMutableCopy());

    slashings.set(state.getSlashings().createMutableCopy());

    previousEpochAttestations.set(state.getPreviousEpochAttestations().createMutableCopy());
    currentEpochAttestations.set(state.getCurrentEpochAttestations().createMutableCopy());

    previousCrosslinks.set(state.getPreviousCrosslinks().createMutableCopy());
    currentCrosslinks.set(state.getCurrentCrosslinks().createMutableCopy());

    justificationBits.set(state.getJustificationBits());
    previousJustifiedCheckpoint.set(state.getPreviousJustifiedCheckpoint());
    currentJustifiedCheckpoint.set(state.getCurrentJustifiedCheckpoint());
    finalizedCheckpoint.set(state.getFinalizedCheckpoint());

    obsHelper.addAllListeners(state.getAllUpdateListeners());
  }

  @Override
  public Map<String, UpdateListener> getAllUpdateListeners() {
    return obsHelper.getAllUpdateListeners();
  }

  @Override
  public UpdateListener getUpdateListener(
      String observerId, Supplier<UpdateListener> listenerFactory) {
    return obsHelper.getUpdateListener(observerId, listenerFactory);
  }

  @Override
  public BeaconState createImmutable() {
    return new BeaconStateImpl(this, specConstants);
  }

  @Override
  public SlotNumber getSlot() {
    return slot.get();
  }

  @Override
  public void setSlot(SlotNumber slot) {
    this.slot.set(slot);
  }

  @Override
  public Time getGenesisTime() {
    return genesisTime.get();
  }

  @Override
  public void setGenesisTime(Time genesisTime) {
    this.genesisTime.set(genesisTime);
  }

  @Override
  public Fork getFork() {
    return fork.get();
  }

  @Override
  public void setFork(Fork fork) {
    this.fork.set(fork);
  }

  @Override
  public WriteList<ValidatorIndex, ValidatorRecord> getValidators() {
    return validators.get();
  }

  public void setValidators(WriteList<ValidatorIndex, ValidatorRecord> validators) {
    this.validators.get().replaceAll(validators);
  }

  @Override
  public WriteList<ValidatorIndex, Gwei> getBalances() {
    return balances.get();
  }

  public void setBalances(WriteList<ValidatorIndex, Gwei> balances) {
    this.balances.get().replaceAll(balances);
  }

  @Override
  public WriteList<EpochNumber, Hash32> getRandaoMixes() {
    return randaoMixes.get();
  }

  public void setRandaoMixes(WriteList<EpochNumber, Hash32> randaoMixes) {
    this.randaoMixes.get().replaceAll(randaoMixes);
  }

  @Override
  public ShardNumber getStartShard() {
    return startShard.get();
  }

  @Override
  public void setStartShard(ShardNumber startShard) {
    this.startShard.set(startShard);
  }

  public WriteList<Integer, PendingAttestation> getPreviousEpochAttestations() {
    return previousEpochAttestations.get();
  }

  public void setPreviousEpochAttestations(
      WriteList<Integer, PendingAttestation> previousEpochAttestations) {
    this.previousEpochAttestations.get().replaceAll(previousEpochAttestations);
  }

  public WriteList<Integer, PendingAttestation> getCurrentEpochAttestations() {
    return currentEpochAttestations.get();
  }

  public void setCurrentEpochAttestations(
      WriteList<Integer, PendingAttestation> currentEpochAttestations) {
    this.currentEpochAttestations.get().replaceAll(currentEpochAttestations);
  }

  @Override
  public Bitvector getJustificationBits() {
    return justificationBits.get();
  }

  @Override
  public void setJustificationBits(Bitvector justificationBits) {
    this.justificationBits.set(justificationBits);
  }

  @Override
  public WriteList<ShardNumber, Crosslink> getPreviousCrosslinks() {
    return previousCrosslinks.get();
  }

  public void setPreviousCrosslinks(WriteList<ShardNumber, Crosslink> previousCrosslinks) {
    this.previousCrosslinks.get().replaceAll(previousCrosslinks);
  }

  @Override
  public WriteList<ShardNumber, Crosslink> getCurrentCrosslinks() {
    return currentCrosslinks.get();
  }

  public void setCurrentCrosslinks(WriteList<ShardNumber, Crosslink> currentCrosslinks) {
    this.currentCrosslinks.get().replaceAll(currentCrosslinks);
  }

  @Override
  public WriteList<SlotNumber, Hash32> getBlockRoots() {
    return blockRoots.get();
  }

  public void setBlockRoots(WriteList<SlotNumber, Hash32> blockRoots) {
    this.blockRoots.get().replaceAll(blockRoots);
  }

  @Override
  public WriteList<SlotNumber, Hash32> getStateRoots() {
    return stateRoots.get();
  }

  public void setStateRoots(WriteList<SlotNumber, Hash32> stateRoots) {
    this.stateRoots.get().replaceAll(stateRoots);
  }

  @Override
  public WriteList<EpochNumber, Hash32> getActiveIndexRoots() {
    return activeIndexRoots.get();
  }

  public void setActiveIndexRoots(WriteList<EpochNumber, Hash32> activeIndexRoots) {
    this.activeIndexRoots.get().replaceAll(activeIndexRoots);
  }

  @Override
  public WriteList<EpochNumber, Hash32> getCompactCommitteesRoots() {
    return compactCommitteesRoots.get();
  }

  public void setCompactCommitteesRoots(WriteList<EpochNumber, Hash32> compactCommitteesRoots) {
    this.compactCommitteesRoots.get().replaceAll(compactCommitteesRoots);
  }

  @Override
  public WriteList<EpochNumber, Gwei> getSlashings() {
    return slashings.get();
  }

  public void setSlashings(WriteList<EpochNumber, Gwei> slashings) {
    this.slashings.get().replaceAll(slashings);
  }

  @Override
  public BeaconBlockHeader getLatestBlockHeader() {
    return latestBlockHeader.get();
  }

  @Override
  public void setLatestBlockHeader(BeaconBlockHeader latestBlockHeader) {
    this.latestBlockHeader.set(latestBlockHeader);
  }

  public WriteList<Integer, Hash32> getHistoricalRoots() {
    return historicalRoots.get();
  }

  public void setHistoricalRoots(WriteList<Integer, Hash32> historicalRoots) {
    this.historicalRoots.get().replaceAll(historicalRoots);
  }

  @Override
  public Eth1Data getEth1Data() {
    return eth1Data.get();
  }

  @Override
  public void setEth1Data(Eth1Data latestEth1Data) {
    this.eth1Data.set(latestEth1Data);
  }

  @Override
  public WriteList<Integer, Eth1Data> getEth1DataVotes() {
    return eth1DataVotes.get();
  }

  @Override
  public void setEth1DataVotes(WriteList<Integer, Eth1Data> eth1DataVotes) {
    this.eth1DataVotes.get().replaceAll(eth1DataVotes);
  }

  @Override
  public UInt64 getEth1DepositIndex() {
    return eth1DepositIndex.get();
  }

  @Override
  public void setEth1DepositIndex(UInt64 depositIndex) {
    this.eth1DepositIndex.set(depositIndex);
  }

  @Override
  public Checkpoint getPreviousJustifiedCheckpoint() {
    return previousJustifiedCheckpoint.get();
  }

  @Override
  public Checkpoint getCurrentJustifiedCheckpoint() {
    return currentJustifiedCheckpoint.get();
  }

  @Override
  public Checkpoint getFinalizedCheckpoint() {
    return finalizedCheckpoint.get();
  }

  public void setPreviousJustifiedCheckpoint(Checkpoint previousJustifiedCheckpoint) {
    this.previousJustifiedCheckpoint.set(previousJustifiedCheckpoint);
  }

  public void setCurrentJustifiedCheckpoint(Checkpoint currentJustifiedCheckpoint) {
    this.currentJustifiedCheckpoint.set(currentJustifiedCheckpoint);
  }

  public void setFinalizedCheckpoint(Checkpoint finalizedCheckpoint) {
    this.finalizedCheckpoint.set(finalizedCheckpoint);
  }

  /** ******* List Getters/Setter for serialization ********* */
  @Override
  public MutableBeaconState createMutableCopy() {
    return new BeaconStateImpl(this, specConstants);
  }

  @Override
  public boolean equals(Object obj) {
    return equalsHelper((BeaconState) obj);
  }

  @Override
  public String toString() {
    return toStringShort(null);
  }
}
