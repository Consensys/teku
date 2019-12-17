package tech.pegasys.artemis.storage;

import com.google.common.eventbus.AllowConcurrentEvents;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.annotation.CheckReturnValue;
import tech.pegasys.artemis.storage.events.StoreDiskUpdateCompleteEvent;
import tech.pegasys.artemis.storage.events.StoreDiskUpdateEvent;
import tech.pegasys.artemis.util.async.AsyncEventTracker;

public class TransactionCommitter {
  private final AsyncEventTracker<Long, Optional<RuntimeException>> tracker;

  public TransactionCommitter(final EventBus eventBus) {
    this.tracker = new AsyncEventTracker<>(eventBus);
    eventBus.register(this);
  }

  @CheckReturnValue
  public CompletableFuture<Void> commit(final Store.Transaction transaction) {
    final StoreDiskUpdateEvent updateEvent = transaction.precommit();
    return tracker
        .sendRequest(updateEvent.getTransactionId(), updateEvent)
        .thenApply(
            error -> {
              if (error.isPresent()) {
                throw error.get();
              }
              transaction.commit();
              return null;
            });
  }

  @Subscribe
  @AllowConcurrentEvents
  void onResponse(final StoreDiskUpdateCompleteEvent event) {
    tracker.onResponse(event.getTransactionId(), event.getError());
  }
}
