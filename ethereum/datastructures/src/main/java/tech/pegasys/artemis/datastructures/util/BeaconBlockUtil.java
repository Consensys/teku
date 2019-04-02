package tech.pegasys.artemis.datastructures.util;

import com.google.common.primitives.UnsignedLong;
import tech.pegasys.artemis.datastructures.Constants;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.util.hashtree.HashTreeUtil;

import java.util.ArrayList;

public class BeaconBlockUtil {
  /**
   * Return the block header corresponding to a
   * block with ``state_root`` set to ``ZERO_HASH``.
   * @param block
   * @return
   */
  public static BeaconBlockHeader get_temporary_block_header(BeaconBlock block) {
    return new BeaconBlockHeader(
            UnsignedLong.valueOf(block.getSlot()),
            block.getPrevious_block_root(),
            Constants.ZERO_HASH,
            HashTreeUtil.hash_tree_root(block.getBody().toBytes()),
            block.getSignature());
  }

  /**
   *  Get an empty ``BeaconBlock``.
   * @return
   */
  public static BeaconBlock get_empty_block() {
    return BeaconBlock(
            Constants.GENESIS_SLOT,
            Constants.ZERO_HASH,
            Constants.ZERO_HASH,
            new BeaconBlock(
                    Constants.EMPTY_SIGNATURE,
                    new Eth1Data(
                            Constants.ZERO_HASH,
                            Constants.ZERO_HASH),
                    new ArrayList(),
                    new ArrayList(),
                    new ArrayList(),
                    new ArrayList(),
                    new ArrayList(),
                    new ArrayList(),
                    ),
            Constants.EMPTY_SIGNATURE,
            );
  }
}
