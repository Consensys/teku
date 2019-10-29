/*
 * Copyright 2018 ConsenSys AG.
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

package tech.pegasys.artemis.util.uint;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class UInt64s {

  public static <C extends UInt64> C max(C v1, C v2) {
    return v1.compareTo(v2) >= 0 ? v1 : v2;
  }

  public static <C extends UInt64> C min(C v1, C v2) {
    return v1.compareTo(v2) < 0 ? v1 : v2;
  }

  public static Stream<UInt64> range(UInt64 fromInclusive, UInt64 toExclusive) {
    return StreamSupport.stream(iterate(fromInclusive, toExclusive).spliterator(), false);
  }

  public static Iterable<UInt64> iterate(UInt64 fromInclusive, UInt64 toExclusive) {
    class Iter implements Iterator<UInt64> {
      private UInt64 cur;
      private UInt64 end;
      private boolean increasing;

      private Iter(UInt64 from, UInt64 to, boolean increasing) {
        this.increasing = increasing;
        this.cur = from;
        this.end = to;
      }

      @Override
      public boolean hasNext() {
        return increasing ? cur.compareTo(end) < 0 : cur.compareTo(end) > 0;
      }

      @Override
      public UInt64 next() {
        if (!hasNext()) throw new NoSuchElementException();
        UInt64 ret = cur;
        cur = increasing ? cur.increment() : cur.decrement();
        return ret;
      }
    }
    return () -> new Iter(fromInclusive, toExclusive, true);
  }
}
