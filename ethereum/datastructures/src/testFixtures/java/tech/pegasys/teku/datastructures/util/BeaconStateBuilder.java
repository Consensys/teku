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

package tech.pegasys.teku.datastructures.util;

import static com.google.common.base.Preconditions.checkNotNull;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_start_slot_at_epoch;
import static tech.pegasys.teku.util.config.Constants.EPOCHS_PER_ETH1_VOTING_PERIOD;
import static tech.pegasys.teku.util.config.Constants.SLOTS_PER_EPOCH;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.state.Fork;
import tech.pegasys.teku.datastructures.state.PendingAttestation;
import tech.pegasys.teku.datastructures.state.Validator;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.Bitvector;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;
import tech.pegasys.teku.ssz.SSZTypes.SSZVector;
import tech.pegasys.teku.util.config.Constants;

public class BeaconStateBuilder {
  private final DataStructureUtil dataStructureUtil;
  private final int defaultValidatorCount;
  private final int defaultItemsInSSZLists;

  private UInt64 genesisTime;
  private Bytes32 genesisValidatorsRoot;
  private UInt64 slot;
  private Fork fork;
  private BeaconBlockHeader latestBlockHeader;
  private SSZVector<Bytes32> blockRoots;
  private SSZVector<Bytes32> stateRoots;
  private SSZList<Bytes32> historicalRoots;
  private Eth1Data eth1Data;
  private SSZList<Eth1Data> eth1DataVotes;
  private UInt64 eth1DepositIndex;
  private SSZList<? extends Validator> validators;
  private SSZList<UInt64> balances;
  private SSZVector<Bytes32> randaoMixes;
  private SSZVector<UInt64> slashings;
  private SSZList<PendingAttestation> previousEpochAttestations;
  private SSZList<PendingAttestation> currentEpochAttestations;
  private Bitvector justificationBits;
  private Checkpoint previousJustifiedCheckpoint;
  private Checkpoint currentJustifiedCheckpoint;
  private Checkpoint finalizedCheckpoint;

  private BeaconStateBuilder(
      final DataStructureUtil dataStructureUtil,
      final int defaultValidatorCount,
      final int defaultItemsInSSZLists) {
    this.dataStructureUtil = dataStructureUtil;
    this.defaultValidatorCount = defaultValidatorCount;
    this.defaultItemsInSSZLists = defaultItemsInSSZLists;
    initDefaults();
  }

  public static BeaconStateBuilder create(
      final DataStructureUtil dataStructureUtil,
      final int defaultValidatorCount,
      final int defaultItemsInSSZLists) {
    return new BeaconStateBuilder(dataStructureUtil, defaultValidatorCount, defaultItemsInSSZLists);
  }

  public BeaconState build() {
    return BeaconState.create(
        genesisTime,
        genesisValidatorsRoot,
        slot,
        fork,
        latestBlockHeader,
        blockRoots,
        stateRoots,
        historicalRoots,
        eth1Data,
        eth1DataVotes,
        eth1DepositIndex,
        validators,
        balances,
        randaoMixes,
        slashings,
        previousEpochAttestations,
        currentEpochAttestations,
        justificationBits,
        previousJustifiedCheckpoint,
        currentJustifiedCheckpoint,
        finalizedCheckpoint);
  }

  private void initDefaults() {
    genesisTime = dataStructureUtil.randomUInt64();
    genesisValidatorsRoot = dataStructureUtil.randomBytes32();
    slot = dataStructureUtil.randomUInt64();
    fork = dataStructureUtil.randomFork();
    latestBlockHeader = dataStructureUtil.randomBeaconBlockHeader();
    blockRoots =
        dataStructureUtil.randomSSZVector(
            Bytes32.ZERO, Constants.SLOTS_PER_HISTORICAL_ROOT, dataStructureUtil::randomBytes32);
    stateRoots =
        dataStructureUtil.randomSSZVector(
            Bytes32.ZERO, Constants.SLOTS_PER_HISTORICAL_ROOT, dataStructureUtil::randomBytes32);
    historicalRoots =
        dataStructureUtil.randomSSZList(
            Bytes32.class,
            defaultItemsInSSZLists,
            Constants.HISTORICAL_ROOTS_LIMIT,
            dataStructureUtil::randomBytes32);
    eth1Data = dataStructureUtil.randomEth1Data();
    eth1DataVotes =
        dataStructureUtil.randomSSZList(
            Eth1Data.class,
            EPOCHS_PER_ETH1_VOTING_PERIOD * SLOTS_PER_EPOCH,
            dataStructureUtil::randomEth1Data);
    eth1DepositIndex = dataStructureUtil.randomUInt64();
    validators =
        dataStructureUtil.randomSSZList(
            Validator.class,
            defaultValidatorCount,
            Constants.VALIDATOR_REGISTRY_LIMIT,
            dataStructureUtil::randomValidator);
    balances =
        dataStructureUtil.randomSSZList(
            UInt64.class,
            defaultValidatorCount,
            Constants.VALIDATOR_REGISTRY_LIMIT,
            dataStructureUtil::randomUInt64);
    randaoMixes =
        dataStructureUtil.randomSSZVector(
            Bytes32.ZERO, Constants.EPOCHS_PER_HISTORICAL_VECTOR, dataStructureUtil::randomBytes32);
    slashings =
        dataStructureUtil.randomSSZVector(
            UInt64.ZERO, Constants.EPOCHS_PER_SLASHINGS_VECTOR, dataStructureUtil::randomUInt64);
    previousEpochAttestations =
        dataStructureUtil.randomSSZList(
            PendingAttestation.class,
            defaultItemsInSSZLists,
            Constants.MAX_ATTESTATIONS * Constants.SLOTS_PER_EPOCH,
            dataStructureUtil::randomPendingAttestation);
    currentEpochAttestations =
        dataStructureUtil.randomSSZList(
            PendingAttestation.class,
            defaultItemsInSSZLists,
            Constants.MAX_ATTESTATIONS * Constants.SLOTS_PER_EPOCH,
            dataStructureUtil::randomPendingAttestation);
    justificationBits = dataStructureUtil.randomBitvector(Constants.JUSTIFICATION_BITS_LENGTH);
    previousJustifiedCheckpoint = dataStructureUtil.randomCheckpoint();
    currentJustifiedCheckpoint = dataStructureUtil.randomCheckpoint();
    finalizedCheckpoint = dataStructureUtil.randomCheckpoint();
  }

  public BeaconStateBuilder genesisTime(final UInt64 genesisTime) {
    checkNotNull(genesisTime);
    this.genesisTime = genesisTime;
    return this;
  }

  public BeaconStateBuilder genesisValidatorsRoot(final Bytes32 genesisValidatorsRoot) {
    checkNotNull(genesisValidatorsRoot);
    this.genesisValidatorsRoot = genesisValidatorsRoot;
    return this;
  }

  public BeaconStateBuilder slot(final UInt64 slot) {
    checkNotNull(slot);
    this.slot = slot;
    return this;
  }

  public BeaconStateBuilder setSlotToStartOfEpoch(final UInt64 epoch) {
    checkNotNull(epoch);
    return slot(compute_start_slot_at_epoch(epoch));
  }

  public BeaconStateBuilder fork(final Fork fork) {
    checkNotNull(fork);
    this.fork = fork;
    return this;
  }

  public BeaconStateBuilder latestBlockHeader(final BeaconBlockHeader latestBlockHeader) {
    checkNotNull(latestBlockHeader);
    this.latestBlockHeader = latestBlockHeader;
    return this;
  }

  public BeaconStateBuilder blockRoots(final SSZVector<Bytes32> blockRoots) {
    checkNotNull(blockRoots);
    this.blockRoots = blockRoots;
    return this;
  }

  public BeaconStateBuilder stateRoots(final SSZVector<Bytes32> stateRoots) {
    checkNotNull(stateRoots);
    this.stateRoots = stateRoots;
    return this;
  }

  public BeaconStateBuilder historicalRoots(final SSZList<Bytes32> historicalRoots) {
    checkNotNull(historicalRoots);
    this.historicalRoots = historicalRoots;
    return this;
  }

  public BeaconStateBuilder eth1Data(final Eth1Data eth1Data) {
    checkNotNull(eth1Data);
    this.eth1Data = eth1Data;
    return this;
  }

  public BeaconStateBuilder eth1DataVotes(final SSZList<Eth1Data> eth1DataVotes) {
    checkNotNull(eth1DataVotes);
    this.eth1DataVotes = eth1DataVotes;
    return this;
  }

  public BeaconStateBuilder eth1DepositIndex(final UInt64 eth1DepositIndex) {
    checkNotNull(eth1DepositIndex);
    this.eth1DepositIndex = eth1DepositIndex;
    return this;
  }

  public BeaconStateBuilder validators(final SSZList<? extends Validator> validators) {
    checkNotNull(validators);
    this.validators = validators;
    return this;
  }

  public BeaconStateBuilder balances(final SSZList<UInt64> balances) {
    checkNotNull(balances);
    this.balances = balances;
    return this;
  }

  public BeaconStateBuilder randaoMixes(final SSZVector<Bytes32> randaoMixes) {
    checkNotNull(randaoMixes);
    this.randaoMixes = randaoMixes;
    return this;
  }

  public BeaconStateBuilder slashings(final SSZVector<UInt64> slashings) {
    checkNotNull(slashings);
    this.slashings = slashings;
    return this;
  }

  public BeaconStateBuilder previousEpochAttestations(
      final SSZList<PendingAttestation> previousEpochAttestations) {
    checkNotNull(previousEpochAttestations);
    this.previousEpochAttestations = previousEpochAttestations;
    return this;
  }

  public BeaconStateBuilder currentEpochAttestations(
      final SSZList<PendingAttestation> currentEpochAttestations) {
    checkNotNull(currentEpochAttestations);
    this.currentEpochAttestations = currentEpochAttestations;
    return this;
  }

  public BeaconStateBuilder justificationBits(final Bitvector justificationBits) {
    checkNotNull(justificationBits);
    this.justificationBits = justificationBits;
    return this;
  }

  public BeaconStateBuilder previousJustifiedCheckpoint(
      final Checkpoint previousJustifiedCheckpoint) {
    checkNotNull(previousJustifiedCheckpoint);
    this.previousJustifiedCheckpoint = previousJustifiedCheckpoint;
    return this;
  }

  public BeaconStateBuilder currentJustifiedCheckpoint(
      final Checkpoint currentJustifiedCheckpoint) {
    checkNotNull(currentJustifiedCheckpoint);
    this.currentJustifiedCheckpoint = currentJustifiedCheckpoint;
    return this;
  }

  public BeaconStateBuilder finalizedCheckpoint(final Checkpoint finalizedCheckpoint) {
    checkNotNull(finalizedCheckpoint);
    this.finalizedCheckpoint = finalizedCheckpoint;
    return this;
  }

  public BeaconStateBuilder setJustifiedCheckpointsToEpoch(final UInt64 epoch) {
    final Checkpoint checkpoint = new Checkpoint(epoch, dataStructureUtil.randomBytes32());
    previousJustifiedCheckpoint(checkpoint);
    currentJustifiedCheckpoint(checkpoint);
    return this;
  }

  public BeaconStateBuilder setFinalizedCheckpointToEpoch(final UInt64 epoch) {
    return finalizedCheckpoint(new Checkpoint(epoch, dataStructureUtil.randomBytes32()));
  }
}
