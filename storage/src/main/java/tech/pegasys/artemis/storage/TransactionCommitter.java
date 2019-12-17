package tech.pegasys.artemis.storage;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.annotation.CheckReturnValue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.artemis.storage.events.StoreDiskUpdateCompleteEvent;
import tech.pegasys.artemis.storage.events.StoreDiskUpdateEvent;
import tech.pegasys.artemis.util.async.AsyncEventTracker;

public class TransactionCommitter {
  private static final Logger LOG = LogManager.getLogger();
  private final AsyncEventTracker<Long, Optional<RuntimeException>> tracker;

  public TransactionCommitter(final EventBus eventBus) {
    this.tracker = new AsyncEventTracker<>(eventBus);
    eventBus.register(this);
  }

  @CheckReturnValue
  public CompletableFuture<Void> commit(final Store.Transaction transaction) {
    LOG.debug("Committing transaction");
    final StoreDiskUpdateEvent updateEvent = transaction.precommit();
    return tracker
        .sendRequest(updateEvent.getTransactionId(), updateEvent)
        .thenApply(
            error -> {
              if (error.isPresent()) {
                LOG.error("Transaction failed");
                throw error.get();
              }
              LOG.debug("Transaction complete");
              return null;
            });
  }

  @Subscribe
  void onResponse(final StoreDiskUpdateCompleteEvent event) {
    tracker.onResponse(event.getTransactionId(), event.getError());
  }
}
