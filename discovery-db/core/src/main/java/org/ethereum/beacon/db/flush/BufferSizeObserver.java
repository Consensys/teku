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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ethereum.beacon.db.source.WriteBuffer;

/**
 * Flushing strategy that observes a size of given buffer and emits a flush whenever size limit is
 * exceeded.
 */
public class BufferSizeObserver implements DatabaseFlusher {

  private static final Logger logger = LogManager.getLogger(BufferSizeObserver.class);

  /** A buffer. */
  private final WriteBuffer buffer;
  /** A commit track. Aids forced flushes consistency. */
  private final WriteBuffer commitTrack;
  /** A limit of buffer size in bytes. */
  private final long bufferSizeLimit;

  BufferSizeObserver(WriteBuffer buffer, WriteBuffer commitTrack, long bufferSizeLimit) {
    this.buffer = buffer;
    this.commitTrack = commitTrack;
    this.bufferSizeLimit = bufferSizeLimit;
  }

  public static <K, V> BufferSizeObserver create(WriteBuffer<K, V> buffer, long bufferSizeLimit) {
    WriteBuffer<K, V> commitTrack = new WriteBuffer<>(buffer, false);
    return new BufferSizeObserver(buffer, commitTrack, bufferSizeLimit);
  }

  @Override
  public void flush() {
    buffer.flush();
  }

  @Override
  public void commit() {
    commitTrack.flush();
    if (buffer.evaluateSize() >= bufferSizeLimit) {
      logger.debug(
          "Flush db buffer due to size limit: {} >= {}", buffer.evaluateSize(), bufferSizeLimit);
      flush();
    }
  }
}
