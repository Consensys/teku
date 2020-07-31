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

package tech.pegasys.teku.util.unsignedlong;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.primitives.UnsignedLong;
import org.junit.jupiter.api.Test;

public class UnsignedLongMathTest {

  @Test
  public void max_firstValueIsLarger() {
    final UnsignedLong a = UnsignedLong.valueOf(2);
    final UnsignedLong b = UnsignedLong.valueOf(1);

    final UnsignedLong result = UnsignedLongMath.max(a, b);
    assertThat(result).isEqualTo(a);
  }

  @Test
  public void max_secondValueIsLarger() {
    final UnsignedLong a = UnsignedLong.valueOf(1);
    final UnsignedLong b = UnsignedLong.valueOf(2);

    final UnsignedLong result = UnsignedLongMath.max(a, b);
    assertThat(result).isEqualTo(b);
  }

  @Test
  public void max_valuesAreEqual() {
    final UnsignedLong a = UnsignedLong.valueOf(10);
    final UnsignedLong b = UnsignedLong.valueOf(10);

    final UnsignedLong result = UnsignedLongMath.max(a, b);
    assertThat(result).isEqualTo(a);
    assertThat(result).isEqualTo(b);
  }

  @Test
  public void min_firstValueIsLarger() {
    final UnsignedLong a = UnsignedLong.valueOf(2);
    final UnsignedLong b = UnsignedLong.valueOf(1);

    final UnsignedLong result = UnsignedLongMath.min(a, b);
    assertThat(result).isEqualTo(b);
  }

  @Test
  public void min_secondValueIsLarger() {
    final UnsignedLong a = UnsignedLong.valueOf(1);
    final UnsignedLong b = UnsignedLong.valueOf(2);

    final UnsignedLong result = UnsignedLongMath.min(a, b);
    assertThat(result).isEqualTo(a);
  }

  @Test
  public void min_valuesAreEqual() {
    final UnsignedLong a = UnsignedLong.valueOf(10);
    final UnsignedLong b = UnsignedLong.valueOf(10);

    final UnsignedLong result = UnsignedLongMath.min(a, b);
    assertThat(result).isEqualTo(a);
    assertThat(result).isEqualTo(b);
  }
}
