package net.consensys.blocktree;

import net.consensys.beaconchain.datastructures.blocks.BeaconBlock;

import org.web3j.abi.datatypes.generated.Int64;

public final class BlockTree {

  private BeaconBlock head;
  private Int64 height;

  public BlockTree() {

  }

}
