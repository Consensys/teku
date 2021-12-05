/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.infrastructure.ssz.sos;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeUtil;

public class SszLengthBounds {
  public static final SszLengthBounds ZERO = new SszLengthBounds(0, 0);
  private final long min;
  private final long max;

  public static SszLengthBounds ofBits(long fixedSize) {
    return new SszLengthBounds(fixedSize, fixedSize);
  }

  public static SszLengthBounds ofBits(long min, long max) {
    return new SszLengthBounds(min, max);
  }

  public static SszLengthBounds ofBytes(long fixedSize) {
    return new SszLengthBounds(fixedSize * 8, fixedSize * 8);
  }

  public static SszLengthBounds ofBytes(long min, long max) {
    return new SszLengthBounds(min * 8, max * 8);
  }

  private SszLengthBounds(final long min, final long max) {
    this.min = min;
    this.max = max;
  }

  public long getMinBits() {
    return min;
  }

  public long getMaxBits() {
    return max;
  }

  public long getMinBytes() {
    return TreeUtil.bitsCeilToBytes(min);
  }

  public long getMaxBytes() {
    return TreeUtil.bitsCeilToBytes(max);
  }

  public SszLengthBounds ceilToBytes() {
    return SszLengthBounds.ofBytes(getMinBytes(), getMaxBytes());
  }

  public SszLengthBounds add(final SszLengthBounds other) {
    return new SszLengthBounds(this.min + other.min, this.max + other.max);
  }

  public SszLengthBounds addBytes(final int moreBytes) {
    return addBits(moreBytes * 8);
  }

  public SszLengthBounds addBits(final int moreBits) {
    return new SszLengthBounds(this.min + moreBits, this.max + moreBits);
  }

  public SszLengthBounds mul(final long factor) {
    return new SszLengthBounds(this.min * factor, this.max * factor);
  }

  public SszLengthBounds or(final SszLengthBounds other) {
    return new SszLengthBounds(Long.min(this.min, other.min), Long.max(this.max, other.max));
  }

  public boolean isWithinBounds(final long lengthBytes) {
    return lengthBytes >= getMinBytes() && lengthBytes <= getMaxBytes();
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
    return getMinBits() == that.getMinBits() && getMaxBits() == that.getMaxBits();
  }

  @Override
  public int hashCode() {
    return Objects.hash(getMinBits(), getMaxBits());
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("min", fromBits(getMinBits()))
        .add("max", fromBits(getMaxBits()))
        .toString();
  }

  private static String fromBits(long bits) {
    long bytes = bits / 8;
    return "" + bytes + ((bits & 7) == 0 ? "" : "(+" + (bits - bytes * 8) + " bits)");
  }
}
