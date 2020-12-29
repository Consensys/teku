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

package tech.pegasys.teku.datastructures.state;

import com.google.common.base.MoreObjects;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import jdk.jfr.Label;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.Bitvector;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;
import tech.pegasys.teku.ssz.SSZTypes.SSZVector;
import tech.pegasys.teku.ssz.backing.ContainerViewRead;
import tech.pegasys.teku.ssz.backing.ViewRead;
import tech.pegasys.teku.ssz.backing.cache.IntCache;
import tech.pegasys.teku.ssz.backing.cache.SoftRefIntCache;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.type.CompositeViewType;
import tech.pegasys.teku.ssz.backing.type.ContainerViewType;
import tech.pegasys.teku.ssz.backing.view.ContainerViewReadImpl;
import tech.pegasys.teku.util.config.Constants;

public class BeaconStateImpl extends ContainerViewReadImpl
    implements BeaconState, BeaconStateCache {

  // The number of SimpleSerialize basic types in this SSZ Container/POJO.
  public static final int SSZ_FIELD_COUNT = 14;

  @Label("sos-ignore")
  private final TransitionCaches transitionCaches;

  // Versioning
  @SuppressWarnings("unused")
  private final UInt64 genesis_time = null;

  @SuppressWarnings("unused")
  private final Bytes32 genesis_validators_root = null;

  @SuppressWarnings("unused")
  private final UInt64 slot = null;

  @SuppressWarnings("unused")
  private final Fork fork = null; // For versioning hard forks

  // History
  @SuppressWarnings("unused")
  private final BeaconBlockHeader latest_block_header = null;

  @SuppressWarnings("unused")
  private final SSZVector<Bytes32> block_roots =
      SSZVector.createMutable(
          Bytes32.class,
          Constants.SLOTS_PER_HISTORICAL_ROOT); // Vector of length SLOTS_PER_HISTORICAL_ROOT

  @SuppressWarnings("unused")
  private final SSZVector<Bytes32> state_roots =
      SSZVector.createMutable(
          Bytes32.class,
          Constants.SLOTS_PER_HISTORICAL_ROOT); // Vector of length SLOTS_PER_HISTORICAL_ROOT

  @SuppressWarnings("unused")
  private final SSZList<Bytes32> historical_roots =
      SSZList.createMutable(
          Bytes32.class, Constants.HISTORICAL_ROOTS_LIMIT); // Bounded by HISTORICAL_ROOTS_LIMIT

  // Ethereum 1.0 chain data
  @SuppressWarnings("unused")
  private final Eth1Data eth1_data = null;

  @SuppressWarnings("unused")
  private final SSZList<Eth1Data> eth1_data_votes =
      SSZList.createMutable(
          Eth1Data.class,
          Constants.EPOCHS_PER_ETH1_VOTING_PERIOD
              * Constants.SLOTS_PER_EPOCH); // List Bounded by EPOCHS_PER_ETH1_VOTING_PERIOD *
  // SLOTS_PER_PERIOD

  @SuppressWarnings("unused")
  private final UInt64 eth1_deposit_index = null;

  // Validator registry
  @SuppressWarnings("unused")
  private final SSZList<Validator> validators =
      SSZList.createMutable(
          Validator.class,
          Constants.VALIDATOR_REGISTRY_LIMIT); // List Bounded by VALIDATOR_REGISTRY_LIMIT

  @SuppressWarnings("unused")
  private final SSZList<UInt64> balances =
      SSZList.createMutable(
          UInt64.class,
          Constants.VALIDATOR_REGISTRY_LIMIT); // List Bounded by VALIDATOR_REGISTRY_LIMIT

  @SuppressWarnings("unused")
  private final SSZVector<Bytes32> randao_mixes =
      SSZVector.createMutable(
          Bytes32.class,
          Constants.EPOCHS_PER_HISTORICAL_VECTOR); // Vector of length EPOCHS_PER_HISTORICAL_VECTOR

  // Slashings
  @SuppressWarnings("unused")
  private final SSZVector<UInt64> slashings =
      SSZVector.createMutable(
          UInt64.class,
          Constants.EPOCHS_PER_SLASHINGS_VECTOR); // Vector of length EPOCHS_PER_SLASHINGS_VECTOR

  // Attestations
  @SuppressWarnings("unused")
  private final SSZList<PendingAttestation> previous_epoch_attestations =
      SSZList.createMutable(
          PendingAttestation.class,
          Constants.MAX_ATTESTATIONS
              * Constants.SLOTS_PER_EPOCH); // List bounded by MAX_ATTESTATIONS * SLOTS_PER_EPOCH

  @SuppressWarnings("unused")
  private final SSZList<PendingAttestation> current_epoch_attestations =
      SSZList.createMutable(
          PendingAttestation.class,
          Constants.MAX_ATTESTATIONS
              * Constants.SLOTS_PER_EPOCH); // List bounded by MAX_ATTESTATIONS * SLOTS_PER_EPOCH

  // Finality
  @SuppressWarnings("unused")
  private final Bitvector justification_bits =
      new Bitvector(
          Constants.JUSTIFICATION_BITS_LENGTH); // Bitvector bounded by JUSTIFICATION_BITS_LENGTH

  @SuppressWarnings("unused")
  private final Checkpoint previous_justified_checkpoint = null;

  @SuppressWarnings("unused")
  private final Checkpoint current_justified_checkpoint = null;

  @SuppressWarnings("unused")
  private final Checkpoint finalized_checkpoint = null;

  public BeaconStateImpl() {
    super(BeaconState.getSSZType());
    transitionCaches = TransitionCaches.createNewEmpty();
  }

  BeaconStateImpl(
      CompositeViewType<?> type,
      TreeNode backingNode,
      IntCache<ViewRead> cache,
      TransitionCaches transitionCaches) {
    super(type, backingNode, cache);
    this.transitionCaches = transitionCaches;
  }

  BeaconStateImpl(ContainerViewType<? extends ContainerViewRead> type, TreeNode backingNode) {
    super(type, backingNode);
    transitionCaches = TransitionCaches.createNewEmpty();
  }

  public BeaconStateImpl(
      // Versioning
      UInt64 genesis_time,
      Bytes32 genesis_validators_root,
      UInt64 slot,
      Fork fork,

      // History
      BeaconBlockHeader latest_block_header,
      SSZVector<Bytes32> block_roots,
      SSZVector<Bytes32> state_roots,
      SSZList<Bytes32> historical_roots,

      // Eth1
      Eth1Data eth1_data,
      SSZList<Eth1Data> eth1_data_votes,
      UInt64 eth1_deposit_index,

      // Registry
      SSZList<? extends Validator> validators,
      SSZList<UInt64> balances,

      // Randomness
      SSZVector<Bytes32> randao_mixes,

      // Slashings
      SSZVector<UInt64> slashings,

      // Attestations
      SSZList<PendingAttestation> previous_epoch_attestations,
      SSZList<PendingAttestation> current_epoch_attestations,

      // Finality
      Bitvector justification_bits,
      Checkpoint previous_justified_checkpoint,
      Checkpoint current_justified_checkpoint,
      Checkpoint finalized_checkpoint) {

    super(
        BeaconState.getSSZType(),
        BeaconState.create(
                genesis_time,
                genesis_validators_root,
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
                finalized_checkpoint)
            .getBackingNode());

    transitionCaches = TransitionCaches.createNewEmpty();
  }

  @Override
  public <E1 extends Exception, E2 extends Exception, E3 extends Exception> BeaconState updated(
      Mutator<E1, E2, E3> mutator) throws E1, E2, E3 {
    MutableBeaconState writableCopy = createWritableCopyPriv();
    mutator.mutate(writableCopy);
    return writableCopy.commitChanges();
  }

  @Override
  public int getSSZFieldCount() {
    return SSZ_FIELD_COUNT
        + getFork().getSSZFieldCount()
        + getLatest_block_header().getSSZFieldCount()
        + getEth1_data().getSSZFieldCount()
        + getPrevious_justified_checkpoint().getSSZFieldCount()
        + getCurrent_justified_checkpoint().getSSZFieldCount()
        + getFinalized_checkpoint().getSSZFieldCount();
  }

  @Override
  public List<Bytes> get_fixed_parts() {
    return List.of(
        SSZ.encodeUInt64(getGenesis_time().longValue()),
        getGenesis_validators_root(),
        SSZ.encodeUInt64(getSlot().longValue()),
        SimpleOffsetSerializer.serialize(getFork()),
        SimpleOffsetSerializer.serialize(getLatest_block_header()),
        SSZ.encode(writer -> writer.writeFixedBytesVector(getBlock_roots().asList())),
        SSZ.encode(writer -> writer.writeFixedBytesVector(getState_roots().asList())),
        Bytes.EMPTY,
        SimpleOffsetSerializer.serialize(getEth1_data()),
        Bytes.EMPTY,
        SSZ.encodeUInt64(getEth1_deposit_index().longValue()),
        Bytes.EMPTY,
        Bytes.EMPTY,
        SSZ.encode(writer -> writer.writeFixedBytesVector(getRandao_mixes().asList())),
        SSZ.encode(
            writer ->
                writer.writeFixedBytesVector(
                    getSlashings().stream()
                        .map(slashing -> SSZ.encodeUInt64(slashing.longValue()))
                        .collect(Collectors.toList()))),
        Bytes.EMPTY,
        Bytes.EMPTY,
        getJustification_bits().serialize(),
        SimpleOffsetSerializer.serialize(getPrevious_justified_checkpoint()),
        SimpleOffsetSerializer.serialize(getCurrent_justified_checkpoint()),
        SimpleOffsetSerializer.serialize(getFinalized_checkpoint()));
  }

  @Override
  public List<Bytes> get_variable_parts() {
    List<Bytes> variablePartsList =
        new ArrayList<>(
            List.of(
                Bytes.EMPTY,
                Bytes.EMPTY,
                Bytes.EMPTY,
                Bytes.EMPTY,
                Bytes.EMPTY,
                Bytes.EMPTY,
                Bytes.EMPTY));
    variablePartsList.add(
        SSZ.encode(writer -> writer.writeFixedBytesVector(getHistorical_roots().asList())));
    variablePartsList.add(Bytes.EMPTY);
    variablePartsList.add(SimpleOffsetSerializer.serializeFixedCompositeList(getEth1_data_votes()));
    variablePartsList.add(Bytes.EMPTY);
    variablePartsList.add(SimpleOffsetSerializer.serializeFixedCompositeList(getValidators()));
    // TODO (#2396): The below lines are a hack while Tuweni SSZ/SOS is being upgraded.
    variablePartsList.add(
        Bytes.fromHexString(
            getBalances().stream()
                .map(value -> SSZ.encodeUInt64(value.longValue()).toHexString().substring(2))
                .collect(Collectors.joining())));
    variablePartsList.addAll(List.of(Bytes.EMPTY, Bytes.EMPTY));
    variablePartsList.add(
        SimpleOffsetSerializer.serializeVariableCompositeList(getPrevious_epoch_attestations()));
    variablePartsList.add(
        SimpleOffsetSerializer.serializeVariableCompositeList(getCurrent_epoch_attestations()));
    variablePartsList.addAll(List.of(Bytes.EMPTY, Bytes.EMPTY, Bytes.EMPTY));
    variablePartsList.addAll(
        Collections.nCopies(getPrevious_justified_checkpoint().getSSZFieldCount(), Bytes.EMPTY));
    variablePartsList.addAll(
        Collections.nCopies(getCurrent_justified_checkpoint().getSSZFieldCount(), Bytes.EMPTY));
    variablePartsList.addAll(
        Collections.nCopies(getFinalized_checkpoint().getSSZFieldCount(), Bytes.EMPTY));
    return variablePartsList;
  }

  @Override
  public int hashCode() {
    return hashCode(this);
  }

  static int hashCode(BeaconState state) {
    return state.hashTreeRoot().slice(0, 4).toInt();
  }

  @Override
  public boolean equals(Object obj) {
    return equals(this, obj);
  }

  static boolean equals(BeaconState state, Object obj) {
    if (Objects.isNull(obj)) {
      return false;
    }

    if (state == obj) {
      return true;
    }

    if (!(obj instanceof BeaconState)) {
      return false;
    }

    BeaconState other = (BeaconState) obj;
    return state.hashTreeRoot().equals(other.hashTreeRoot());
  }

  @Override
  public Bytes32 hash_tree_root() {
    return hashTreeRoot();
  }

  @Override
  public TransitionCaches getTransitionCaches() {
    return transitionCaches;
  }

  static String toString(BeaconState state) {
    return MoreObjects.toStringHelper(state)
        .add("genesis_time", state.getGenesis_time())
        .add("genesis_validators_root", state.getGenesis_validators_root())
        .add("slot", state.getSlot())
        .add("fork", state.getFork())
        .add("latest_block_header", state.getLatest_block_header())
        .add("block_roots", state.getBlock_roots())
        .add("state_roots", state.getState_roots())
        .add("historical_roots", state.getHistorical_roots())
        .add("eth1_data", state.getEth1_data())
        .add("eth1_data_votes", state.getEth1_data_votes())
        .add("eth1_deposit_index", state.getEth1_deposit_index())
        .add("validators", state.getValidators())
        .add("balances", state.getBalances())
        .add("randao_mixes", state.getRandao_mixes())
        .add("slashings", state.getSlashings())
        .add("previous_epoch_attestations", state.getPrevious_epoch_attestations())
        .add("current_epoch_attestations", state.getCurrent_epoch_attestations())
        .add("justification_bits", state.getJustification_bits())
        .add("previous_justified_checkpoint", state.getPrevious_justified_checkpoint())
        .add("current_justified_checkpoint", state.getCurrent_justified_checkpoint())
        .add("finalized_checkpoint", state.getFinalized_checkpoint())
        .toString();
  }

  @Override
  protected IntCache<ViewRead> createCache() {
    return new SoftRefIntCache<>(super::createCache);
  }

  @Override
  public String toString() {
    return toString(this);
  }

  private MutableBeaconState createWritableCopyPriv() {
    return new MutableBeaconStateImpl(this);
  }
}
