/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.ssz.sos;

import com.google.common.base.MoreObjects;
import java.util.Objects;

public class SszLengthBounds {
  public static final SszLengthBounds ZERO = new SszLengthBounds(0, 0);
  private final long min;
  private final long max;

  public SszLengthBounds(final long fixedSize) {
    this(fixedSize, fixedSize);
  }

  public SszLengthBounds(final long min, final long max) {
    this.min = min;
    this.max = max;
  }

  public long getMin() {
    return min;
  }

  public long getMax() {
    return max;
  }

  public boolean isWithinBounds(final long length) {
    return length >= min && length <= max;
  }

  public SszLengthBounds add(final SszLengthBounds other) {
    return new SszLengthBounds(this.min + other.min, this.max + other.max);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final SszLengthBounds that = (SszLengthBounds) o;
    return getMin() == that.getMin() && getMax() == that.getMax();
  }

  @Override
  public int hashCode() {
    return Objects.hash(getMin(), getMax());
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("min", min).add("max", max).toString();
  }
}
