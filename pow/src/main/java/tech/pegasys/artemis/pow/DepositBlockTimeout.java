package tech.pegasys.artemis.pow;

import com.google.common.eventbus.Subscribe;
import java.util.Date;
import org.web3j.protocol.core.methods.response.EthBlock.Block;
import tech.pegasys.artemis.pow.event.Deposit;

final class DepositBlockTimeout {
  private static final int EVENT_TIMEOUT = 5000;
  private final BlockBatcher batcher;
  private long lastPublishTime = 0;

  DepositBlockTimeout(final BlockBatcher batcher) {
    this.batcher = batcher;
  }

  public synchronized void onDepositEvent(final Block block, final Deposit deposit) {
    batcher.onDepositEvent(block, deposit);
    lastPublishTime = System.currentTimeMillis();
  }

  @Subscribe
  public synchronized void onTick(final Date date) {
    if (lastPublishTime != 0 && date.getTime() - lastPublishTime > EVENT_TIMEOUT) {
      batcher.forcePublishPendingBlock();
      lastPublishTime = 0;
    }
  }
}
