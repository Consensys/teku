package net.consensys.beaconchain.datastructures.blocks;

import net.consensys.beaconchain.ethereum.core.Hash;

public class AttestationSignedData {

  private Uint64 slot;
  private Uint64 shard;
  private Hash[] parent_hashes;
  private Hash shard_block_hash;
  private Hash last_crosslink_hash;
  private Hash shard_block_combined_data_root;
  private Uint64 justified_slot;

  public AttestationSignedData() {

  }

}
