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

package tech.pegasys.artemis.datastructures.state;

import com.google.common.primitives.UnsignedLong;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.Copyable;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.util.SSZTypes.Bitvector;
import tech.pegasys.artemis.util.SSZTypes.SSZList;
import tech.pegasys.artemis.util.SSZTypes.SSZVector;

public final class BeaconStateWithCache extends BeaconState {

  private final TransitionCaches transitionCaches;

  public BeaconStateWithCache() {
    super();
    transitionCaches = TransitionCaches.createNewEmpty();
  }

  public BeaconStateWithCache(
      // Versioning
      UnsignedLong genesis_time,
      UnsignedLong slot,
      Fork fork,

      // History
      BeaconBlockHeader latest_block_header,
      SSZVector<Bytes32> block_roots,
      SSZVector<Bytes32> state_roots,
      SSZList<Bytes32> historical_roots,

      // Eth1
      Eth1Data eth1_data,
      SSZList<Eth1Data> eth1_data_votes,
      UnsignedLong eth1_deposit_index,

      // Registry
      SSZList<Validator> validators,
      SSZList<UnsignedLong> balances,

      // Randomness
      SSZVector<Bytes32> randao_mixes,

      // Slashings
      SSZVector<UnsignedLong> slashings,

      // Attestations
      SSZList<PendingAttestation> previous_epoch_attestations,
      SSZList<PendingAttestation> current_epoch_attestations,

      // Finality
      Bitvector justification_bits,
      Checkpoint previous_justified_checkpoint,
      Checkpoint current_justified_checkpoint,
      Checkpoint finalized_checkpoint) {
    super(
        genesis_time,
        slot,
        fork,
        latest_block_header,
        block_roots,
        state_roots,
        historical_roots,
        eth1_data,
        eth1_data_votes,
        eth1_deposit_index,
        validators,
        balances,
        randao_mixes,
        slashings,
        previous_epoch_attestations,
        current_epoch_attestations,
        justification_bits,
        previous_justified_checkpoint,
        current_justified_checkpoint,
        finalized_checkpoint);
    transitionCaches = TransitionCaches.createNewEmpty();
  }

  private BeaconStateWithCache(
      UnsignedLong genesis_time,
      UnsignedLong slot,
      Fork fork,
      BeaconBlockHeader latest_block_header,
      SSZVector<Bytes32> block_roots,
      SSZVector<Bytes32> state_roots,
      SSZList<Bytes32> historical_roots,
      Eth1Data eth1_data,
      SSZList<Eth1Data> eth1_data_votes,
      UnsignedLong eth1_deposit_index,
      SSZList<Validator> validators,
      SSZList<UnsignedLong> balances,
      SSZVector<Bytes32> randao_mixes,
      SSZVector<UnsignedLong> slashings,
      SSZList<PendingAttestation> previous_epoch_attestations,
      SSZList<PendingAttestation> current_epoch_attestations,
      Bitvector justification_bits,
      Checkpoint previous_justified_checkpoint,
      Checkpoint current_justified_checkpoint,
      Checkpoint finalized_checkpoint,
      TransitionCaches transitionCaches) {
    super(
        genesis_time,
        slot,
        fork,
        latest_block_header,
        block_roots,
        state_roots,
        historical_roots,
        eth1_data,
        eth1_data_votes,
        eth1_deposit_index,
        validators,
        balances,
        randao_mixes,
        slashings,
        previous_epoch_attestations,
        current_epoch_attestations,
        justification_bits,
        previous_justified_checkpoint,
        current_justified_checkpoint,
        finalized_checkpoint);
    this.transitionCaches = transitionCaches;
  }

  public static BeaconStateWithCache deepCopy(BeaconState state) {
    final SSZList<Validator> validators =
        copyList(
            state.getValidators(),
            new SSZList<>(Validator.class, state.getValidators().getMaxSize()));
    final SSZList<PendingAttestation> previous_epoch_attestations =
        copyList(
            state.getPrevious_epoch_attestations(),
            new SSZList<>(
                PendingAttestation.class, state.getPrevious_epoch_attestations().getMaxSize()));
    final SSZList<PendingAttestation> current_epoch_attestations =
        copyList(
            state.getCurrent_epoch_attestations(),
            new SSZList<>(
                PendingAttestation.class, state.getCurrent_epoch_attestations().getMaxSize()));

    return new BeaconStateWithCache(
        state.getGenesis_time(),
        state.getSlot(),
        new Fork(state.getFork()),

        // History
        new BeaconBlockHeader(state.getLatest_block_header()),
        new SSZVector<>(state.getBlock_roots()),
        new SSZVector<>(state.getState_roots()),
        new SSZList<>(state.getHistorical_roots()),

        // Eth1
        new Eth1Data(state.getEth1_data()),
        new SSZList<>(state.getEth1_data_votes()),
        state.getEth1_deposit_index(),

        // Registry
        validators,
        new SSZList<>(state.getBalances()),

        // Randomness
        new SSZVector<>(state.getRandao_mixes()),

        // Slashings
        new SSZVector<>(state.getSlashings()),

        // Attestations
        previous_epoch_attestations,
        current_epoch_attestations,

        // Finality
        state.getJustification_bits().copy(),
        state.getPrevious_justified_checkpoint(),
        state.getCurrent_justified_checkpoint(),
        state.getFinalized_checkpoint(),
        state instanceof BeaconStateWithCache
            ? ((BeaconStateWithCache) state).transitionCaches.copy()
            : TransitionCaches.createNewEmpty());
  }

  /**
   * Creates a BeaconStateWithCache with empty caches from the given BeaconState.
   *
   * @param state state to create from
   * @return created state with empty caches
   */
  public static BeaconStateWithCache fromBeaconState(BeaconState state) {
    if (state instanceof BeaconStateWithCache) return (BeaconStateWithCache) state;
    return new BeaconStateWithCache(
        state.getGenesis_time(),
        state.getSlot(),
        state.getFork(),
        state.getLatest_block_header(),
        state.getBlock_roots(),
        state.getState_roots(),
        state.getHistorical_roots(),
        state.getEth1_data(),
        state.getEth1_data_votes(),
        state.getEth1_deposit_index(),
        state.getValidators(),
        state.getBalances(),
        state.getRandao_mixes(),
        state.getSlashings(),
        state.getPrevious_epoch_attestations(),
        state.getCurrent_epoch_attestations(),
        state.getJustification_bits(),
        state.getPrevious_justified_checkpoint(),
        state.getCurrent_justified_checkpoint(),
        state.getFinalized_checkpoint());
  }

  public static TransitionCaches getTransitionCaches(BeaconState state) {
    return state instanceof BeaconStateWithCache
        ? ((BeaconStateWithCache) state).getTransitionCaches()
        : TransitionCaches.getNoOp();
  }

  public TransitionCaches getTransitionCaches() {
    return transitionCaches;
  }

  private static <S extends Copyable<S>, T extends List<S>> T copyList(
      T sourceList, T destinationList) {
    for (S sourceItem : sourceList) {
      destinationList.add(sourceItem.copy());
    }
    return destinationList;
  }
}
