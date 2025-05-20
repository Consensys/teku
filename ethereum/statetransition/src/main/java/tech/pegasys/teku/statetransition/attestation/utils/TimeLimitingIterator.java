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

package tech.pegasys.teku.statetransition.attestation.utils;

import java.util.Iterator;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

public record TimeLimitingIterator<T>(
    LongSupplier nanosSupplier, long timeLimitNanos, Iterator<T> delegate, LongConsumer onTimeLimit)
    implements Iterator<T> {

  @Override
  public boolean hasNext() {
    if (nanosSupplier.getAsLong() <= timeLimitNanos) {
      return delegate.hasNext();
    }

    onTimeLimit.accept(timeLimitNanos);

    return false;
  }

  @Override
  public T next() {
    return delegate.next();
  }
}
