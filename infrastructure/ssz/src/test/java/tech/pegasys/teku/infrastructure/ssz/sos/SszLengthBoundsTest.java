/*
 * Copyright Consensys Software Inc., 2026
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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class SszLengthBoundsTest {

  @Test
  void ofBytes_withLargeValues_shouldNotOverflow() {
    final SszLengthBounds bounds = SszLengthBounds.ofBytes(0, Long.MAX_VALUE / 2);
    // (Long.MAX_VALUE / 2) * 8 would overflow without saturation
    assertThat(bounds.getMaxBits()).isEqualTo(Long.MAX_VALUE);
    assertThat(bounds.getMaxBytes()).isPositive();
  }

  @Test
  void ofBytes_withMaxValue_shouldSaturate() {
    final SszLengthBounds bounds = SszLengthBounds.ofBytes(0, Long.MAX_VALUE);
    assertThat(bounds.getMaxBits()).isEqualTo(Long.MAX_VALUE);
    assertThat(bounds.getMaxBytes()).isPositive();
  }

  @Test
  void ofBytes_withNormalValues_shouldNotSaturate() {
    final SszLengthBounds bounds = SszLengthBounds.ofBytes(10, 100);
    assertThat(bounds.getMinBits()).isEqualTo(80);
    assertThat(bounds.getMaxBits()).isEqualTo(800);
    assertThat(bounds.getMinBytes()).isEqualTo(10);
    assertThat(bounds.getMaxBytes()).isEqualTo(100);
  }

  @Test
  void add_withMaxValueMax_shouldSaturate() {
    final SszLengthBounds a = SszLengthBounds.ofBits(0, Long.MAX_VALUE);
    final SszLengthBounds b = SszLengthBounds.ofBits(0, 1000);
    final SszLengthBounds result = a.add(b);
    assertThat(result.getMaxBits()).isEqualTo(Long.MAX_VALUE);
    assertThat(result.getMaxBytes()).isPositive();
  }

  @Test
  void add_withNormalValues_shouldNotSaturate() {
    final SszLengthBounds a = SszLengthBounds.ofBits(10, 100);
    final SszLengthBounds b = SszLengthBounds.ofBits(20, 200);
    final SszLengthBounds result = a.add(b);
    assertThat(result.getMinBits()).isEqualTo(30);
    assertThat(result.getMaxBits()).isEqualTo(300);
  }

  @Test
  void mul_withLargeFactor_shouldSaturate() {
    final SszLengthBounds bounds = SszLengthBounds.ofBits(0, Long.MAX_VALUE);
    final SszLengthBounds result = bounds.mul(100);
    assertThat(result.getMaxBits()).isEqualTo(Long.MAX_VALUE);
    assertThat(result.getMaxBytes()).isPositive();
  }

  @Test
  void mul_withNormalValues_shouldNotSaturate() {
    final SszLengthBounds bounds = SszLengthBounds.ofBits(10, 100);
    final SszLengthBounds result = bounds.mul(5);
    assertThat(result.getMinBits()).isEqualTo(50);
    assertThat(result.getMaxBits()).isEqualTo(500);
  }

  @Test
  void addBits_withMaxValueBase_shouldSaturate() {
    final SszLengthBounds bounds = SszLengthBounds.ofBits(0, Long.MAX_VALUE);
    final SszLengthBounds result = bounds.addBits(32);
    assertThat(result.getMaxBits()).isEqualTo(Long.MAX_VALUE);
  }

  @Test
  void addBytes_withMaxValueBase_shouldSaturate() {
    final SszLengthBounds bounds = SszLengthBounds.ofBits(0, Long.MAX_VALUE);
    final SszLengthBounds result = bounds.addBytes(4);
    assertThat(result.getMaxBits()).isEqualTo(Long.MAX_VALUE);
  }

  @Test
  void isWithinBounds_withSaturatedMax_shouldAcceptAllReasonableSizes() {
    final SszLengthBounds bounds = SszLengthBounds.ofBits(0, Long.MAX_VALUE);
    assertThat(bounds.isWithinBounds(0)).isTrue();
    assertThat(bounds.isWithinBounds(1000)).isTrue();
    // getMaxBytes() converts Long.MAX_VALUE bits to bytes, so max bytes is ~1.15e18
    // which is still enormous — any realistic SSZ payload fits
    assertThat(bounds.getMaxBytes()).isGreaterThan(1_000_000_000_000L);
  }

  @Test
  void ceilToBytes_withMaxValue_shouldNotOverflow() {
    final SszLengthBounds bounds = SszLengthBounds.ofBits(0, Long.MAX_VALUE);
    final SszLengthBounds ceiled = bounds.ceilToBytes();
    assertThat(ceiled.getMaxBytes()).isPositive();
  }
}
