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

package org.ethereum.beacon.discovery;

import javax.annotation.Nonnull;

/**
 * This DataSource type is backed up by another {@link DataSource}e storage (upstream). Upstream
 * source may have different Key or/and Value type. In this case this source would also act as a
 * converter between those types
 *
 * <p>Upstream source generally acts as data originator on data queries All updates are generally
 * forwarded to the upstream source either immediately or on <code>flush()</code> invocation
 */
public interface LinkedDataSource<KeyType, ValueType, UpKeyType, UpValueType>
    extends DataSource<KeyType, ValueType> {

  /** @return Upstream {@link DataSource} */
  @Nonnull
  DataSource<UpKeyType, UpValueType> getUpstream();

  /**
   * Optional method.
   *
   * <p>Normally the whole tree of data sources is created during initialization, but it could be
   * useful sometimes to modify this pipeline after creation.
   *
   * <p>Not every implementation may support 'hot swap', i.e. when <code>setUpstream()</code> is
   * called after data flow is already started. *
   *
   * @throws IllegalStateException when this source doesn't support 'hot swap' and this source
   *     started processing data already
   */
  default void setUpstream(@Nonnull DataSource<UpKeyType, UpValueType> newUpstream) {
    throw new UnsupportedOperationException();
  };
}
