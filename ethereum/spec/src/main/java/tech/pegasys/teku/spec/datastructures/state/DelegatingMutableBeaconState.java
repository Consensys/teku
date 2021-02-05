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

package tech.pegasys.teku.spec.datastructures.state;

import java.util.Optional;
import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.state.Fork;
import tech.pegasys.teku.datastructures.state.ForkInfo;
import tech.pegasys.teku.datastructures.state.PendingAttestation;
import tech.pegasys.teku.datastructures.state.Validator;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.Bitvector;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;
import tech.pegasys.teku.ssz.SSZTypes.SSZMutableList;
import tech.pegasys.teku.ssz.SSZTypes.SSZMutableVector;
import tech.pegasys.teku.ssz.SSZTypes.SSZVector;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives;

public class DelegatingMutableBeaconState implements MutableBeaconState {

  protected final MutableBeaconState state;

  public DelegatingMutableBeaconState(final MutableBeaconState state) {
    this.state = state;
  }

  @Override
  public UInt64 getGenesis_time() {
    return state.getGenesis_time();
  }

  @Override
  public Bytes32 getGenesis_validators_root() {
    return state.getGenesis_validators_root();
  }

  @Override
  public UInt64 getSlot() {
    return state.getSlot();
  }

  @Override
  public Fork getFork() {
    return state.getFork();
  }

  @Override
  public ForkInfo getForkInfo() {
    return state.getForkInfo();
  }

  @Override
  public BeaconBlockHeader getLatest_block_header() {
    return state.getLatest_block_header();
  }

  @Override
  public SSZVector<Bytes32> getBlock_roots() {
    return state.getBlock_roots();
  }

  @Override
  public SSZVector<Bytes32> getState_roots() {
    return state.getState_roots();
  }

  @Override
  public SSZList<Bytes32> getHistorical_roots() {
    return state.getHistorical_roots();
  }

  @Override
  public Eth1Data getEth1_data() {
    return state.getEth1_data();
  }

  @Override
  public SSZList<Eth1Data> getEth1_data_votes() {
    return state.getEth1_data_votes();
  }

  @Override
  public UInt64 getEth1_deposit_index() {
    return state.getEth1_deposit_index();
  }

  @Override
  public SSZList<Validator> getValidators() {
    return state.getValidators();
  }

  @Override
  public SSZList<UInt64> getBalances() {
    return state.getBalances();
  }

  @Override
  public SSZVector<Bytes32> getRandao_mixes() {
    return state.getRandao_mixes();
  }

  @Override
  public SSZVector<UInt64> getSlashings() {
    return state.getSlashings();
  }

  @Override
  public Bitvector getJustification_bits() {
    return state.getJustification_bits();
  }

  @Override
  public Checkpoint getPrevious_justified_checkpoint() {
    return state.getPrevious_justified_checkpoint();
  }

  @Override
  public Checkpoint getCurrent_justified_checkpoint() {
    return state.getCurrent_justified_checkpoint();
  }

  @Override
  public Checkpoint getFinalized_checkpoint() {
    return state.getFinalized_checkpoint();
  }

  @Override
  public Optional<SSZList<PendingAttestation>> maybeGetPrevious_epoch_attestations() {
    return state.maybeGetPrevious_epoch_attestations();
  }

  @Override
  public Optional<SSZList<PendingAttestation>> maybeGetCurrent_epoch_attestations() {
    return state.maybeGetCurrent_epoch_attestations();
  }

  @Override
  public Optional<SSZList<SSZVector<SszPrimitives.SszBit>>> maybeGetPreviousEpochParticipation() {
    return state.maybeGetPreviousEpochParticipation();
  }

  @Override
  public Optional<SSZList<SSZVector<SszPrimitives.SszBit>>> maybeGetCurrentEpochParticipation() {
    return state.maybeGetCurrentEpochParticipation();
  }

  @Override
  public BeaconState update(final Consumer<MutableBeaconState> updater) {
    return state.update(updater);
  }

  @Override
  public void setGenesis_time(final UInt64 genesis_time) {
    state.setGenesis_time(genesis_time);
  }

  @Override
  public void setGenesis_validators_root(final Bytes32 genesis_validators_root) {
    state.setGenesis_validators_root(genesis_validators_root);
  }

  @Override
  public void setSlot(final UInt64 slot) {
    state.setSlot(slot);
  }

  @Override
  public void setFork(final Fork fork) {
    state.setFork(fork);
  }

  @Override
  public void setLatest_block_header(final BeaconBlockHeader latest_block_header) {
    state.setLatest_block_header(latest_block_header);
  }

  @Override
  public void updateBlock_roots(final Consumer<SSZMutableVector<Bytes32>> updater) {
    state.updateBlock_roots(updater);
  }

  @Override
  public void updateState_roots(final Consumer<SSZMutableVector<Bytes32>> updater) {
    state.updateState_roots(updater);
  }

  @Override
  public void updateHistorical_roots(final Consumer<SSZMutableList<Bytes32>> updater) {
    state.updateHistorical_roots(updater);
  }

  @Override
  public void setEth1_data(final Eth1Data eth1_data) {
    state.setEth1_data(eth1_data);
  }

  @Override
  public void updateEth1_data_votes(final Consumer<SSZMutableList<Eth1Data>> updater) {
    state.updateEth1_data_votes(updater);
  }

  @Override
  public void setEth1_deposit_index(final UInt64 eth1_deposit_index) {
    state.setEth1_deposit_index(eth1_deposit_index);
  }

  @Override
  public void updateValidators(final Consumer<SSZMutableList<Validator>> updater) {
    state.updateValidators(updater);
  }

  @Override
  public void updateBalances(final Consumer<SSZMutableList<UInt64>> updater) {
    state.updateBalances(updater);
  }

  @Override
  public void updateRandao_mixes(final Consumer<SSZMutableVector<Bytes32>> updater) {
    state.updateRandao_mixes(updater);
  }

  @Override
  public void updateSlashings(final Consumer<SSZMutableVector<UInt64>> updater) {
    state.updateSlashings(updater);
  }

  @Override
  public void setJustification_bits(final Bitvector justification_bits) {
    state.setJustification_bits(justification_bits);
  }

  @Override
  public void setPrevious_justified_checkpoint(final Checkpoint previous_justified_checkpoint) {
    state.setPrevious_justified_checkpoint(previous_justified_checkpoint);
  }

  @Override
  public void setCurrent_justified_checkpoint(final Checkpoint current_justified_checkpoint) {
    state.setCurrent_justified_checkpoint(current_justified_checkpoint);
  }

  @Override
  public void setFinalized_checkpoint(final Checkpoint finalized_checkpoint) {
    state.setFinalized_checkpoint(finalized_checkpoint);
  }

  @Override
  public void maybeUpdatePrevious_epoch_attestations(
      final Consumer<Optional<SSZMutableList<PendingAttestation>>> updater) {
    state.maybeUpdatePrevious_epoch_attestations(updater);
  }

  @Override
  public void maybeUpdateCurrent_epoch_attestations(
      final Consumer<Optional<SSZMutableList<PendingAttestation>>> updater) {
    state.maybeUpdateCurrent_epoch_attestations(updater);
  }

  @Override
  public void maybeUpdatePreviousEpochParticipation(
      final Consumer<Optional<SSZMutableList<SSZVector<SszPrimitives.SszBit>>>> updater) {
    state.maybeUpdatePreviousEpochParticipation(updater);
  }

  @Override
  public void maybeUpdateCurrentEpochParticipation(
      final Consumer<Optional<SSZMutableList<SSZVector<SszPrimitives.SszBit>>>> updater) {
    state.maybeUpdateCurrentEpochParticipation(updater);
  }
}
