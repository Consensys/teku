/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

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
