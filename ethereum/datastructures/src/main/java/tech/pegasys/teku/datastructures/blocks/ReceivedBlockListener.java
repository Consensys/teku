package tech.pegasys.teku.datastructures.blocks;

import org.apache.tuweni.bytes.Bytes32;

public interface ReceivedBlockListener {
  void accept(Bytes32 blockRoot);
}
