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

package org.ethereum.beacon.db.util;

import java.util.concurrent.locks.Lock;

/**
 * Releases lock after exit from try block.
 *
 * <p>Usage:
 *
 * <pre>
 *    try (AutoCloseableLock l = lock.lock()) {
 *      ...
 *    }
 * </pre>
 */
public final class AutoCloseableLock implements AutoCloseable {

  private final Lock delegate;

  AutoCloseableLock(Lock delegate) {
    this.delegate = delegate;
  }

  public static AutoCloseableLock wrap(Lock delegate) {
    return new AutoCloseableLock(delegate);
  }

  @Override
  public void close() {
    this.unlock();
  }

  public AutoCloseableLock lock() {
    delegate.lock();
    return this;
  }

  public void unlock() {
    delegate.unlock();
  }
}
