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

package tech.pegasys.teku.ssz.ssztypes;

import java.util.stream.IntStream;
import tech.pegasys.teku.ssz.SSZTypes.MutableBitlist;

class DefaultBitlistTest extends AbstractBitlistTest {

  @Override
  protected MutableBitlist createBitlistWithSize(
      final int size, final int maxSize, final int... bits) {
    MutableBitlist bitlist = MutableBitlist.create(size, BITLIST_MAX_SIZE);
    IntStream.of(bits).forEach(bitlist::setBit);
    return bitlist;
  }
}
