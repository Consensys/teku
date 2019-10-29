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

package org.ethereum.beacon.db.source.impl;

import java.util.Optional;
import javax.annotation.Nonnull;
import org.ethereum.beacon.db.source.DataSource;

public class DelegateDataSource<KeyType, ValueType> implements DataSource<KeyType, ValueType> {
  private final DataSource<KeyType, ValueType> delegate;

  public DelegateDataSource(DataSource<KeyType, ValueType> delegate) {
    this.delegate = delegate;
  }

  @Override
  public Optional<ValueType> get(@Nonnull KeyType key) {
    return delegate.get(key);
  }

  @Override
  public void put(@Nonnull KeyType key, @Nonnull ValueType value) {
    delegate.put(key, value);
  }

  @Override
  public void remove(@Nonnull KeyType key) {
    delegate.remove(key);
  }

  @Override
  public void flush() {
    delegate.flush();
  }
}
