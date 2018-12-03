package net.consensys.beaconchain.datastructures.blocks;

import net.consensys.beaconchain.ethereum.core.Hash;

public class AttestationData {

  private Uint64 slot;
  private Uint64 shard;
  private Hash beacon_block_hash;
  private Hash epoch_boundary_hash;
  private Hash shard_block_hash;
  private Hash last_crosslink_hash;
  private Uint64 justified_slot;
  private Hash justified_block_hash;

  public AttestationData() {

  }

}
