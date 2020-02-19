package tech.pegasys.artemis.datastructures.state;

import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.util.SSZTypes.Bitvector;
import tech.pegasys.artemis.util.SSZTypes.SSZMutableList;
import tech.pegasys.artemis.util.SSZTypes.SSZMutableRefList;
import tech.pegasys.artemis.util.SSZTypes.SSZMutableVector;
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

  @Override
  SSZMutableVector<Bytes32> getBlock_roots();

  @Override
  SSZMutableVector<Bytes32> getState_roots();

  @Override
  SSZMutableList<Bytes32> getHistorical_roots();

  // Eth1
  void setEth1_data(Eth1Data eth1_data);

  @Override
  SSZMutableList<Eth1Data> getEth1_data_votes();

  UnsignedLong getEth1_deposit_index();

  void setEth1_deposit_index(UnsignedLong eth1_deposit_index);

  // Registry
  @Override
  SSZMutableRefList<Validator, MutableValidator> getValidators();

  @Override
  SSZMutableList<UnsignedLong> getBalances();

  @Override
  SSZMutableVector<Bytes32> getRandao_mixes();

  // Slashings
  @Override
  SSZMutableVector<UnsignedLong> getSlashings();

  // Attestations
  @Override
  SSZMutableList<PendingAttestation> getPrevious_epoch_attestations();

  @Override
  SSZMutableList<PendingAttestation> getCurrent_epoch_attestations();

  // Finality
  void setJustification_bits(Bitvector justification_bits);

  void setPrevious_justified_checkpoint(Checkpoint previous_justified_checkpoint);

  void setCurrent_justified_checkpoint(Checkpoint current_justified_checkpoint);

  void setFinalized_checkpoint(Checkpoint finalized_checkpoint);

  BeaconState commitChanges();
}
