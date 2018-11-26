package net.consensys.beaconchain.datastructures.blocks;

import net.consensys.beaconchain.ethereum.core.Hash;

import org.web3j.abi.datatypes.Bytes;

public class AttestationRecord {

  private Uint64 slot;
  private Uint64 shard;
  private Hash[] oblique_parent_hashes;
  private Hash shard_block_hash;
  private Hash last_crosslink_hash;
  private Hash shard_block_combined_data_root;
  private Uint64 attester_bitfield;
  private Int64 justified_slot;
  private Hash justified_block_hash;
  private Uint64[] aggregate_sig;

  public AttestationRecord() {

  }

}
