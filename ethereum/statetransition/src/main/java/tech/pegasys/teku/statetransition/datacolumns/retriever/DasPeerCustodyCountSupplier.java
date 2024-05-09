package tech.pegasys.teku.statetransition.datacolumns.retriever;

import org.apache.tuweni.units.bigints.UInt256;

public interface DasPeerCustodyCountSupplier {

  static DasPeerCustodyCountSupplier createStub(int defaultValue) {
    return (__) -> defaultValue;
  }

  int getCustodyCountForPeer(UInt256 nodeId);

}
