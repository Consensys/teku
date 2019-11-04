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

import java.util.function.Function;
import org.ethereum.beacon.discovery.type.BytesValue;

// import org.ethereum.beacon.db.source.impl.XorDataSource;

/** An abstract class that uses {@link XorDataSource} for storage multiplexing. */
public abstract class XorKeyDatabase implements Database {

  private final DataSource<BytesValue, BytesValue> backingDataSource;
  private final Function<BytesValue, BytesValue> sourceNameHasher;

  public XorKeyDatabase(
      DataSource<BytesValue, BytesValue> backingDataSource,
      Function<BytesValue, BytesValue> sourceNameHasher) {
    this.backingDataSource = backingDataSource;
    this.sourceNameHasher = sourceNameHasher;
  }

  @Override
  public DataSource<BytesValue, BytesValue> createStorage(String name) {
    return new XorDataSource<>(
        backingDataSource, sourceNameHasher.apply(BytesValue.wrap(name.getBytes())));
  }

  public DataSource<BytesValue, BytesValue> getBackingDataSource() {
    return backingDataSource;
  }
}
