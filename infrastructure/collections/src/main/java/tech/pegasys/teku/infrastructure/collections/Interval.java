/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.infrastructure.collections;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public record Interval<T extends Comparable<T>>(T start, T end) {
  public Interval(final T start, final T end) {
    if (start.compareTo(end) > 0) {
      throw new IllegalArgumentException(
          String.format("Interval start (%s) must be less or equal than end (%s)", start, end));
    }
    this.start = start;
    this.end = end;
  }

  public boolean contains(final T value) {
    return start.compareTo(value) <= 0 && end.compareTo(value) >= 0;
  }

  public static <T extends Comparable<T>> Interval<T> of(final T start, final T end) {
    return new Interval<>(start, end);
  }

  @SafeVarargs
  public static <T extends Comparable<T>> void checkIntervalsIntersection(
      final Interval<T>... intervals) {
    final List<Interval<T>> intervalList =
        Arrays.stream(intervals).sorted(Comparator.comparing(Interval::start)).toList();
    if (intervalList.size() < 2) {
      return;
    }
    T currentEnd = intervalList.getFirst().end();
    for (Interval<T> interval : intervalList.subList(1, intervalList.size())) {
      if (interval.start().compareTo(currentEnd) <= 0) {
        throw new IllegalArgumentException(
            String.format(
                "Intervals shouldn't intersect, while previous interval end (%S), current interval start start (%s)",
                currentEnd, interval.start()));
      }
      currentEnd = interval.end();
    }
  }
}
