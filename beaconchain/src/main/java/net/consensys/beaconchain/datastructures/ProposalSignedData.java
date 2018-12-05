package net.consensys.beaconchain.datastructures;

import net.consensys.beaconchain.ethereum.core.Hash;
import net.consensys.beaconchain.util.uint.UInt64;

public class ProposalSignedData {

  private UInt64 slot;
  private UInt64 shard;
  private Hash block_hash;

  public ProposalSignedData() {

  }

}
