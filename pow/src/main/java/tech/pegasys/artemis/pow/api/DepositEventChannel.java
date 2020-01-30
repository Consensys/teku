package tech.pegasys.artemis.pow.api;

import tech.pegasys.artemis.pow.event.DepositsFromBlockEvent;

public interface DepositEventChannel {
  void notifyDepositsFromBlock(DepositsFromBlockEvent event);
}
