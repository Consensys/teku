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

package org.ethereum.beacon.db.flush;

import org.ethereum.beacon.db.source.WriteBuffer;

/**
 * Flushing strategy.
 *
 * <p>Used to manage {@link WriteBuffer} flushes to underlying data source.
 *
 * @see InstantFlusher
 * @see BufferSizeObserver
 */
public interface DatabaseFlusher {

  /**
   * Forces a flush to the underlying data source.
   *
   * <p><strong>Note:</strong> an implementation MUST take care of consistency of the data being
   * force flushed.
   */
  void flush();

  /**
   * A client should call this method whenever a buffer contains consistent data that are ready to
   * be flushed.
   *
   * <p><strong>Note:</strong> depending on implementation this method MAY or MAY NOT be thread
   * safe.
   */
  void commit();
}
