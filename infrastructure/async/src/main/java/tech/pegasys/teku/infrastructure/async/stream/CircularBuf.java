/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.infrastructure.async.stream;

import static tech.pegasys.teku.infrastructure.async.stream.Util.noCallBinaryOperator;

import java.util.ArrayDeque;
import java.util.List;
import java.util.stream.Collector;

class CircularBuf<C> {
  final ArrayDeque<C> buf;
  final int maxSize;

  public CircularBuf(int maxSize) {
    buf = new ArrayDeque<>(maxSize);
    this.maxSize = maxSize;
  }

  public void add(C t) {
    if (buf.size() == maxSize) {
      buf.removeFirst();
    }
    buf.add(t);
  }

  public static <T> Collector<T, ?, List<T>> createCollector(int count) {
    return Collector.<T, CircularBuf<T>, List<T>>of(
        () -> new CircularBuf<T>(count),
        CircularBuf::add,
        noCallBinaryOperator(),
        buf -> buf.buf.stream().toList());
  }
}
