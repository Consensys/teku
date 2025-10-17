/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.infrastructure.events;

import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.subscribers.ValueObserver;

public class FutureValueObserver<C> {
  private static final Logger LOG = LogManager.getLogger();
  private final SafeFuture<Consumer<ValueObserver<C>>> subscriptionConsumerFuture =
      new SafeFuture<>();

  public void complete(final Consumer<ValueObserver<C>> consumer) {
    subscriptionConsumerFuture.complete(consumer);
  }

  public void subscribe(final ValueObserver<C> onValueChange) {
    subscriptionConsumerFuture
        .thenAccept(consumer -> consumer.accept(onValueChange))
        .finishDebug(LOG);
  }
}
