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
import org.apache.tuweni.bytes.Bytes;

/** An abstract class that uses {@link XorDataSource} for storage multiplexing. */
public abstract class XorKeyDatabase implements Database {

  private final DataSource<Bytes, Bytes> backingDataSource;
  private final Function<Bytes, Bytes> sourceNameHasher;

  public XorKeyDatabase(
      DataSource<Bytes, Bytes> backingDataSource, Function<Bytes, Bytes> sourceNameHasher) {
    this.backingDataSource = backingDataSource;
    this.sourceNameHasher = sourceNameHasher;
  }

  @Override
  @SuppressWarnings({"DefaultCharset"})
  public DataSource<Bytes, Bytes> createStorage(String name) {
    return new XorDataSource<>(
        backingDataSource, sourceNameHasher.apply(Bytes.wrap(name.getBytes())));
  }

  public DataSource<Bytes, Bytes> getBackingDataSource() {
    return backingDataSource;
  }
}
