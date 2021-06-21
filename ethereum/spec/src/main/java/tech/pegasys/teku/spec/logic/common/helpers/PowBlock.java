package tech.pegasys.teku.spec.logic.common.helpers;

import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.web3j.protocol.core.methods.response.EthBlock;

public class PowBlock {

  public final Bytes32 blockHash;
  public final boolean isProcessed;
  public final boolean isValid;
  public final UInt256 totalDifficulty;
  public final UInt256 difficulty;

  public PowBlock(
      Bytes32 blockHash,
      boolean isProcessed,
      boolean isValid,
      UInt256 totalDifficulty,
      UInt256 difficulty) {
    this.blockHash = blockHash;
    this.isProcessed = isProcessed;
    this.isValid = isValid;
    this.totalDifficulty = totalDifficulty;
    this.difficulty = difficulty;
  }

  PowBlock(EthBlock.Block block) {
    this(
        Bytes32.fromHexString(block.getHash()),
        true,
        true,
        UInt256.valueOf(block.getTotalDifficulty()),
        UInt256.valueOf(block.getDifficulty()));
  }
}
