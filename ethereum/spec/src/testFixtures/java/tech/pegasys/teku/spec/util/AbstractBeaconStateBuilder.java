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

package tech.pegasys.teku.spec.util;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.stream.Collectors.toList;

import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBytes32Vector;
import tech.pegasys.teku.infrastructure.ssz.collections.SszPrimitiveList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszPrimitiveVector;
import tech.pegasys.teku.infrastructure.ssz.collections.SszUInt64List;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;

@SuppressWarnings("unchecked")
abstract class AbstractBeaconStateBuilder<
    TState extends BeaconState,
    TStateMutable extends MutableBeaconState,
    TBuilder extends AbstractBeaconStateBuilder<TState, TStateMutable, TBuilder>> {
  protected final DataStructureUtil dataStructureUtil;
  protected final int defaultValidatorCount;
  protected final int defaultItemsInSSZLists;
  protected final SpecVersion spec;

  private UInt64 genesisTime;
  private Bytes32 genesisValidatorsRoot;
  private UInt64 slot;
  private Fork fork;
  private BeaconBlockHeader latestBlockHeader;
  private SszBytes32Vector blockRoots;
  private SszBytes32Vector stateRoots;
  private SszPrimitiveList<Bytes32, SszBytes32> historicalRoots;
  private Eth1Data eth1Data;
  private SszList<Eth1Data> eth1DataVotes;
  private UInt64 eth1DepositIndex;
  private SszList<Validator> validators;
  private SszUInt64List balances;
  private SszBytes32Vector randaoMixes;
  private SszPrimitiveVector<UInt64, SszUInt64> slashings;
  private SszBitvector justificationBits;
  private Checkpoint previousJustifiedCheckpoint;
  private Checkpoint currentJustifiedCheckpoint;
  private Checkpoint finalizedCheckpoint;

  protected AbstractBeaconStateBuilder(
      final SpecVersion spec,
      final DataStructureUtil dataStructureUtil,
      final int defaultValidatorCount,
      final int defaultItemsInSSZLists) {
    this.spec = spec;
    this.dataStructureUtil = dataStructureUtil;
    this.defaultValidatorCount = defaultValidatorCount;
    this.defaultItemsInSSZLists = defaultItemsInSSZLists;
    initDefaults();
  }

  protected abstract TState getEmptyState();

  protected abstract void setUniqueFields(TStateMutable state);

  public TState build() {
    return (TState)
        getEmptyState()
            .updated(
                state -> {
                  state.setGenesis_time(genesisTime);
                  state.setGenesis_validators_root(genesisValidatorsRoot);
                  state.setSlot(slot);
                  state.setFork(fork);
                  state.setLatest_block_header(latestBlockHeader);
                  state.getBlock_roots().setAll(blockRoots);
                  state.getState_roots().setAll(stateRoots);
                  state.getHistorical_roots().setAll(historicalRoots);
                  state.setEth1_data(eth1Data);
                  state.getEth1_data_votes().setAll(eth1DataVotes);
                  state.setEth1_deposit_index(eth1DepositIndex);
                  state.getValidators().setAll(validators);
                  state.getBalances().setAll(balances);
                  state.getRandao_mixes().setAll(randaoMixes);
                  state.getSlashings().setAll(slashings);
                  state.setJustification_bits(justificationBits);
                  state.setPrevious_justified_checkpoint(previousJustifiedCheckpoint);
                  state.setCurrent_justified_checkpoint(currentJustifiedCheckpoint);
                  state.setFinalized_checkpoint(finalizedCheckpoint);

                  setUniqueFields((TStateMutable) state);
                });
  }

  protected void initDefaults() {

    genesisTime = dataStructureUtil.randomUInt64();
    genesisValidatorsRoot = dataStructureUtil.randomBytes32();
    slot = dataStructureUtil.randomUInt64();
    fork = dataStructureUtil.randomFork();
    latestBlockHeader =
        new BeaconBlockHeader(
            slot,
            dataStructureUtil.randomUInt64(),
            dataStructureUtil.randomBytes32(),
            Bytes32.ZERO,
            dataStructureUtil.randomBytes32());
    blockRoots =
        dataStructureUtil.randomSszBytes32Vector(
            dataStructureUtil.getBeaconStateSchema().getBlockRootsSchema(),
            dataStructureUtil::randomBytes32);
    stateRoots =
        dataStructureUtil.randomSszBytes32Vector(
            dataStructureUtil.getBeaconStateSchema().getStateRootsSchema(),
            dataStructureUtil::randomBytes32);
    historicalRoots =
        dataStructureUtil.randomSszPrimitiveList(
            dataStructureUtil.getBeaconStateSchema().getHistoricalRootsSchema(),
            defaultItemsInSSZLists,
            dataStructureUtil::randomBytes32);
    eth1Data = dataStructureUtil.randomEth1Data();
    eth1DataVotes =
        dataStructureUtil.randomSszList(
            dataStructureUtil.getBeaconStateSchema().getEth1DataVotesSchema(),
            dataStructureUtil.getEpochsPerEth1VotingPeriod() * dataStructureUtil.getSlotsPerEpoch(),
            dataStructureUtil::randomEth1Data);
    eth1DepositIndex = dataStructureUtil.randomUInt64();
    validators =
        dataStructureUtil.randomSszList(
            dataStructureUtil.getBeaconStateSchema().getValidatorsSchema(),
            defaultValidatorCount,
            dataStructureUtil::randomValidator);
    balances =
        dataStructureUtil.randomSszUInt64List(
            dataStructureUtil.getBeaconStateSchema().getBalancesSchema(),
            defaultValidatorCount,
            dataStructureUtil::randomUInt64);
    randaoMixes =
        dataStructureUtil.randomSszBytes32Vector(
            dataStructureUtil.getBeaconStateSchema().getRandaoMixesSchema(),
            dataStructureUtil::randomBytes32);
    slashings =
        dataStructureUtil.randomSszPrimitiveVector(
            dataStructureUtil.getBeaconStateSchema().getSlashingsSchema(),
            dataStructureUtil::randomUInt64);
    justificationBits =
        dataStructureUtil.randomSszBitvector(dataStructureUtil.getJustificationBitsLength());
    previousJustifiedCheckpoint = dataStructureUtil.randomCheckpoint();
    currentJustifiedCheckpoint = dataStructureUtil.randomCheckpoint();
    finalizedCheckpoint = dataStructureUtil.randomCheckpoint();
  }

  public TBuilder genesisTime(final UInt64 genesisTime) {
    checkNotNull(genesisTime);
    this.genesisTime = genesisTime;
    return (TBuilder) this;
  }

  public TBuilder genesisValidatorsRoot(final Bytes32 genesisValidatorsRoot) {
    checkNotNull(genesisValidatorsRoot);
    this.genesisValidatorsRoot = genesisValidatorsRoot;
    return (TBuilder) this;
  }

  public TBuilder slot(final UInt64 slot) {
    checkNotNull(slot);
    this.slot = slot;
    return (TBuilder) this;
  }

  public TBuilder setSlotToStartOfEpoch(final UInt64 epoch) {
    checkNotNull(epoch);
    return slot(dataStructureUtil.computeStartSlotAtEpoch(epoch));
  }

  public TBuilder fork(final Fork fork) {
    checkNotNull(fork);
    this.fork = fork;
    return (TBuilder) this;
  }

  public TBuilder latestBlockHeader(final BeaconBlockHeader latestBlockHeader) {
    checkNotNull(latestBlockHeader);
    this.latestBlockHeader = latestBlockHeader;
    return (TBuilder) this;
  }

  public TBuilder blockRoots(final SszBytes32Vector blockRoots) {
    checkNotNull(blockRoots);
    this.blockRoots = blockRoots;
    return (TBuilder) this;
  }

  public TBuilder blockRoots(final List<Bytes32> roots) {
    checkNotNull(roots);
    this.blockRoots =
        spec.getSchemaDefinitions()
            .getBeaconStateSchema()
            .getBlockRootsSchema()
            .createFromElements(roots.stream().map(SszBytes32::of).collect(toList()));
    return (TBuilder) this;
  }

  public TBuilder stateRoots(final SszBytes32Vector stateRoots) {
    checkNotNull(stateRoots);
    this.stateRoots = stateRoots;
    return (TBuilder) this;
  }

  public TBuilder historicalRoots(final List<Bytes32> roots) {
    checkNotNull(roots);
    this.historicalRoots =
        spec.getSchemaDefinitions()
            .getBeaconStateSchema()
            .getHistoricalRootsSchema()
            .createFromElements(roots.stream().map(SszBytes32::of).collect(toList()));
    return (TBuilder) this;
  }

  public TBuilder historicalRoots(final SszPrimitiveList<Bytes32, SszBytes32> historicalRoots) {
    checkNotNull(historicalRoots);
    this.historicalRoots = historicalRoots;
    return (TBuilder) this;
  }

  public TBuilder eth1Data(final Eth1Data eth1Data) {
    checkNotNull(eth1Data);
    this.eth1Data = eth1Data;
    return (TBuilder) this;
  }

  public TBuilder eth1DataVotes(final SszList<Eth1Data> eth1DataVotes) {
    checkNotNull(eth1DataVotes);
    this.eth1DataVotes = eth1DataVotes;
    return (TBuilder) this;
  }

  public TBuilder eth1DepositIndex(final UInt64 eth1DepositIndex) {
    checkNotNull(eth1DepositIndex);
    this.eth1DepositIndex = eth1DepositIndex;
    return (TBuilder) this;
  }

  public TBuilder validators(final Validator... validators) {
    return validators(
        dataStructureUtil.getBeaconStateSchema().getValidatorsSchema().of(validators));
  }

  public TBuilder validators(final SszList<Validator> validators) {
    checkNotNull(validators);
    this.validators = validators;
    return (TBuilder) this;
  }

  public TBuilder balances(final UInt64... balances) {
    return balances(dataStructureUtil.getBeaconStateSchema().getBalancesSchema().of(balances));
  }

  public TBuilder balances(final SszUInt64List balances) {
    checkNotNull(balances);
    this.balances = balances;
    return (TBuilder) this;
  }

  public TBuilder randaoMixes(final SszBytes32Vector randaoMixes) {
    checkNotNull(randaoMixes);
    this.randaoMixes = randaoMixes;
    return (TBuilder) this;
  }

  public TBuilder slashings(final SszPrimitiveVector<UInt64, SszUInt64> slashings) {
    checkNotNull(slashings);
    this.slashings = slashings;
    return (TBuilder) this;
  }

  public TBuilder justificationBits(final SszBitvector justificationBits) {
    checkNotNull(justificationBits);
    this.justificationBits = justificationBits;
    return (TBuilder) this;
  }

  public TBuilder previousJustifiedCheckpoint(final Checkpoint previousJustifiedCheckpoint) {
    checkNotNull(previousJustifiedCheckpoint);
    this.previousJustifiedCheckpoint = previousJustifiedCheckpoint;
    return (TBuilder) this;
  }

  public TBuilder currentJustifiedCheckpoint(final Checkpoint currentJustifiedCheckpoint) {
    checkNotNull(currentJustifiedCheckpoint);
    this.currentJustifiedCheckpoint = currentJustifiedCheckpoint;
    return (TBuilder) this;
  }

  public TBuilder finalizedCheckpoint(final Checkpoint finalizedCheckpoint) {
    checkNotNull(finalizedCheckpoint);
    this.finalizedCheckpoint = finalizedCheckpoint;
    return (TBuilder) this;
  }

  public TBuilder setJustifiedCheckpointsToEpoch(final UInt64 epoch) {
    final Checkpoint checkpoint = new Checkpoint(epoch, dataStructureUtil.randomBytes32());
    previousJustifiedCheckpoint(checkpoint);
    currentJustifiedCheckpoint(checkpoint);
    return (TBuilder) this;
  }

  public TBuilder setFinalizedCheckpointToEpoch(final UInt64 epoch) {
    return finalizedCheckpoint(new Checkpoint(epoch, dataStructureUtil.randomBytes32()));
  }
}
