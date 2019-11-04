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

package org.ethereum.beacon.discovery.database;

import static java.util.Objects.requireNonNull;

import javax.annotation.Nonnull;

/**
 * Abstract implementation of {@link LinkedDataSource}
 *
 * <p>It can optionally do cascade flush of the upstream {@link DataSource} if the corresponding
 * flag was explicitly specified in the constructor.
 */
public abstract class AbstractLinkedDataSource<KeyType, ValueType, UpKeyType, UpValueType>
    implements LinkedDataSource<KeyType, ValueType, UpKeyType, UpValueType> {

  private final DataSource<UpKeyType, UpValueType> upstreamSource;
  private final boolean upstreamFlush;

  /** Creates an instance with upstream source. Cascade flush is disabled. */
  protected AbstractLinkedDataSource(
      @Nonnull final DataSource<UpKeyType, UpValueType> upstreamSource) {
    this(upstreamSource, false);
  }

  /**
   * Creates an instance with upstream source and cascade flush enabled/disabled
   *
   * @param upstreamFlush whether upstream DataSource should be flushed during <code>this.flush()
   *     </code>
   */
  protected AbstractLinkedDataSource(
      @Nonnull final DataSource<UpKeyType, UpValueType> upstreamSource,
      final boolean upstreamFlush) {
    this.upstreamSource = requireNonNull(upstreamSource);
    this.upstreamFlush = upstreamFlush;
  }

  @Override
  @Nonnull
  public DataSource<UpKeyType, UpValueType> getUpstream() {
    return upstreamSource;
  }

  /**
   * If cascade flush is enabled then call {@link #doFlush()} and then invokes upstream <code>
   * flush()</code> If cascade flush is disabled then just call {@link #doFlush()} The method is
   * made final so all the implementation specific flush operations should be performed in
   * overridden {@link #doFlush()}
   */
  @Override
  public final void flush() {
    doFlush();
    if (upstreamFlush) {
      getUpstream().flush();
    }
  }

  /**
   * Override this method if the implementation needs to propagate collected updates to upstream
   * source. Don't call upstream <code>flush()</code> inside this method, this is performed by
   * {@link #flush()} method By default does nothing.
   */
  protected void doFlush() {}
}
