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

import javax.annotation.Nonnull;
import org.ethereum.beacon.db.source.CodecSource;
import org.ethereum.beacon.db.source.DataSource;
import tech.pegasys.artemis.util.bytes.BytesValue;
import tech.pegasys.artemis.util.bytes.MutableBytesValue;

public class XorDataSource<TValue> extends CodecSource.KeyOnly<BytesValue, TValue, BytesValue> {

  public XorDataSource(
      @Nonnull DataSource<BytesValue, TValue> upstreamSource, BytesValue keyXorModifier) {
    super(upstreamSource, key -> xorLongest(key, keyXorModifier));
  }

  private static BytesValue xorLongest(BytesValue v1, BytesValue v2) {
    BytesValue longVal = v1.size() >= v2.size() ? v1 : v2;
    BytesValue shortVal = v1.size() < v2.size() ? v1 : v2;
    MutableBytesValue ret = longVal.mutableCopy();
    int longLen = longVal.size();
    int shortLen = shortVal.size();
    for (int i = 0; i < shortLen; i++) {
      ret.set(longLen - i - 1, (byte) (ret.get(longLen - i - 1) ^ shortVal.get(shortLen - i - 1)));
    }
    return ret;
  }
}
