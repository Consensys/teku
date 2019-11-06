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

import javax.annotation.Nonnull;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.MutableBytes;

public class XorDataSource<TValue> extends CodecSource.KeyOnly<Bytes, TValue, Bytes> {

  public XorDataSource(@Nonnull DataSource<Bytes, TValue> upstreamSource, Bytes keyXorModifier) {
    super(upstreamSource, key -> xorLongest(key, keyXorModifier));
  }

  private static Bytes xorLongest(Bytes v1, Bytes v2) {
    Bytes longVal = v1.size() >= v2.size() ? v1 : v2;
    Bytes shortVal = v1.size() < v2.size() ? v1 : v2;
    MutableBytes ret = longVal.mutableCopy();
    int longLen = longVal.size();
    int shortLen = shortVal.size();
    for (int i = 0; i < shortLen; i++) {
      ret.set(longLen - i - 1, (byte) (ret.get(longLen - i - 1) ^ shortVal.get(shortLen - i - 1)));
    }
    return ret;
  }
}
