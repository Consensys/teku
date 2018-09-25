package net.consensys.beaconchain.ethereum.mainnet;

import net.consensys.beaconchain.ethereum.core.BlockHeader;
import net.consensys.beaconchain.ethereum.core.Hash;
import net.consensys.beaconchain.ethereum.rlp.RLP;
import net.consensys.beaconchain.util.bytes.BytesValue;

/**
 * Implements the block hashing algorithm for MainNet as per the yellow paper.
 */
public class MainnetBlockHashFunction {

  public static Hash createHash(BlockHeader header) {
    BytesValue rlp = RLP.encode(header::writeTo);
    return Hash.hash(rlp);
  }
}
