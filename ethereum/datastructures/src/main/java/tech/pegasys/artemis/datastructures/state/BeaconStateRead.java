package tech.pegasys.artemis.datastructures.state;

import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.util.SSZTypes.Bitvector;
import tech.pegasys.artemis.util.SSZTypes.SSZContainer;
import tech.pegasys.artemis.util.SSZTypes.SSZListRead;
import tech.pegasys.artemis.util.SSZTypes.SSZVectorRead;
import tech.pegasys.artemis.util.backing.ContainerViewRead;
import tech.pegasys.artemis.util.backing.ViewRead;
import tech.pegasys.artemis.util.hashtree.Merkleizable;
import tech.pegasys.artemis.util.sos.SimpleOffsetSerializable;

public interface BeaconStateRead extends ContainerViewRead<ViewRead>, Merkleizable,
    SimpleOffsetSerializable, SSZContainer {

  // Versioning
  UnsignedLong getGenesis_time();

  UnsignedLong getSlot();

  Fork getFork();

  // History
  BeaconBlockHeader getLatest_block_header();

  SSZVectorRead<Bytes32> getBlock_roots();

  SSZVectorRead<Bytes32> getState_roots();

  SSZListRead<Bytes32> getHistorical_roots();

  // Eth1
  Eth1Data getEth1_data();

  SSZListRead<Eth1Data> getEth1_data_votes();

  UnsignedLong getEth1_deposit_index();

  // Registry
  SSZListRead<ValidatorRead> getValidators();

  SSZListRead<UnsignedLong> getBalances();

  SSZVectorRead<Bytes32> getRandao_mixes();

  // Slashings
  SSZVectorRead<UnsignedLong> getSlashings();

  // Attestations
  SSZListRead<PendingAttestation> getPrevious_epoch_attestations();

  SSZListRead<PendingAttestation> getCurrent_epoch_attestations();

  // Finality
  Bitvector getJustification_bits();

  Checkpoint getPrevious_justified_checkpoint();

  Checkpoint getCurrent_justified_checkpoint();

  Checkpoint getFinalized_checkpoint();

  BeaconStateWrite createWritableCopy();
}
