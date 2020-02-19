package tech.pegasys.artemis.datastructures.state;

import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.util.SSZTypes.Bitvector;
import tech.pegasys.artemis.util.SSZTypes.SSZListWrite;
import tech.pegasys.artemis.util.SSZTypes.SSZListWriteRef;
import tech.pegasys.artemis.util.SSZTypes.SSZVectorWrite;
import tech.pegasys.artemis.util.backing.ContainerViewWriteRef;
import tech.pegasys.artemis.util.backing.ViewRead;
import tech.pegasys.artemis.util.backing.ViewWrite;

public interface MutableBeaconState extends BeaconState,
    ContainerViewWriteRef<ViewRead, ViewWrite> {

  // Versioning

  void setGenesis_time(UnsignedLong genesis_time);

  void setSlot(UnsignedLong slot);

  void setFork(Fork fork);

  // History
  void setLatest_block_header(BeaconBlockHeader latest_block_header);

  // Eth1
  void setEth1_data(Eth1Data eth1_data);

  SSZListWrite<Eth1Data> getEth1_data_votes();

  UnsignedLong getEth1_deposit_index();

  void setEth1_deposit_index(UnsignedLong eth1_deposit_index);

  // Registry
  SSZListWriteRef<Validator, MutableValidator> getValidators();

  SSZListWrite<UnsignedLong> getBalances();

  SSZVectorWrite<Bytes32> getRandao_mixes();

  // Slashings
  SSZVectorWrite<UnsignedLong> getSlashings();

  // Attestations
  SSZListWrite<PendingAttestation> getPrevious_epoch_attestations();

  SSZListWrite<PendingAttestation> getCurrent_epoch_attestations();

  // Finality
  void setJustification_bits(Bitvector justification_bits);

  void setPrevious_justified_checkpoint(Checkpoint previous_justified_checkpoint);

  void setCurrent_justified_checkpoint(Checkpoint current_justified_checkpoint);

  void setFinalized_checkpoint(Checkpoint finalized_checkpoint);

  BeaconState commitChanges();
}
