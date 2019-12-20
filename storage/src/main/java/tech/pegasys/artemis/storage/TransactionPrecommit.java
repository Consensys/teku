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

import com.google.common.eventbus.EventBus;
import javax.annotation.CheckReturnValue;
import tech.pegasys.artemis.storage.events.StoreDiskUpdateEvent;
import tech.pegasys.artemis.util.async.SafeFuture;

public interface TransactionPrecommit {

  static TransactionPrecommit memoryOnly() {
    return event -> SafeFuture.completedFuture(null);
  }

  static TransactionPrecommit storageEnabled(final EventBus eventBus) {
    return new StoreToDiskTransactionPrecommit(eventBus);
  }

  @CheckReturnValue
  SafeFuture<Void> precommit(StoreDiskUpdateEvent updateEvent);
}
