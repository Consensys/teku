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

package tech.pegasys.artemis.datastructures;

import com.google.common.primitives.UnsignedLong;
import java.util.Iterator;

public class UnsignedLongListIterator implements Iterator {
  UnsignedLongList list;
  UnsignedLong currentIndex;

  public UnsignedLongListIterator(UnsignedLongList list) {
    this.list = list;
    this.currentIndex = UnsignedLong.ZERO;
  }

  @Override
  public boolean hasNext() {
    if (!list.size().equals(UnsignedLong.ZERO)
        && list.size().compareTo(this.currentIndex.plus(UnsignedLong.ONE)) > 0) return true;
    return false;
  }

  @Override
  public Object next() {
    if (hasNext()) {
      Object obj = list.get(currentIndex);
      currentIndex = currentIndex.plus(UnsignedLong.ONE);
      return obj;
    }
    return null;
  }
}
