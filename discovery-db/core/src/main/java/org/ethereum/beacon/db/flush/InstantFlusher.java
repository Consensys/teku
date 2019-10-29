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

/** A trivial strategy that flushes data per each commit. */
public class InstantFlusher implements DatabaseFlusher {

  private final WriteBuffer buffer;

  public InstantFlusher(WriteBuffer buffer) {
    this.buffer = buffer;
  }

  @Override
  public void flush() {}

  @Override
  public void commit() {
    buffer.flush();
  }
}
