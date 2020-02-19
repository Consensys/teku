package tech.pegasys.artemis.datastructures.state;

import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.util.SSZTypes.Bitvector;
import tech.pegasys.artemis.util.SSZTypes.SSZContainer;
import tech.pegasys.artemis.util.SSZTypes.SSZList;
import tech.pegasys.artemis.util.SSZTypes.SSZVector;
import tech.pegasys.artemis.util.backing.ContainerViewRead;
import tech.pegasys.artemis.util.backing.ViewRead;
import tech.pegasys.artemis.util.hashtree.Merkleizable;
import tech.pegasys.artemis.util.sos.SimpleOffsetSerializable;

public interface BeaconState extends ContainerViewRead<ViewRead>, Merkleizable,
    SimpleOffsetSerializable, SSZContainer {

  static BeaconState createEmpty() {
    return BeaconStateWithCache.fromBeaconState(new BeaconStateImpl());
  }

  static BeaconState create(

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
      SSZList<ValidatorImpl> validators,
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

    return BeaconStateWithCache.fromBeaconState(new BeaconStateImpl(genesis_time, slot, fork, latest_block_header, block_roots, state_roots,
        historical_roots, eth1_data, eth1_data_votes, eth1_deposit_index, validators, balances,
        randao_mixes, slashings, previous_epoch_attestations, current_epoch_attestations,
        justification_bits, previous_justified_checkpoint, current_justified_checkpoint,
        finalized_checkpoint));
  }

  // Versioning
  UnsignedLong getGenesis_time();

  UnsignedLong getSlot();

  Fork getFork();

  // History
  BeaconBlockHeader getLatest_block_header();

  SSZVector<Bytes32> getBlock_roots();

  SSZVector<Bytes32> getState_roots();

  SSZList<Bytes32> getHistorical_roots();

  // Eth1
  Eth1Data getEth1_data();

  SSZList<Eth1Data> getEth1_data_votes();

  UnsignedLong getEth1_deposit_index();

  // Registry
  SSZList<Validator> getValidators();

  SSZList<UnsignedLong> getBalances();

  SSZVector<Bytes32> getRandao_mixes();

  // Slashings
  SSZVector<UnsignedLong> getSlashings();

  // Attestations
  SSZList<PendingAttestation> getPrevious_epoch_attestations();

  SSZList<PendingAttestation> getCurrent_epoch_attestations();

  // Finality
  Bitvector getJustification_bits();

  Checkpoint getPrevious_justified_checkpoint();

  Checkpoint getCurrent_justified_checkpoint();

  Checkpoint getFinalized_checkpoint();

  MutableBeaconState createWritableCopy();
}
