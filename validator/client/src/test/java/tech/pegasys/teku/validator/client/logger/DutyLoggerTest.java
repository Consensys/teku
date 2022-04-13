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

package tech.pegasys.teku.validator.client.logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.Map;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;

public class DutyLoggerTest {
  private final Spec spec = TestSpecFactory.createDefault();
  private final SomeDutyLogger dutyLogger = new SomeDutyLogger(spec);

  @Test
  public void shouldCalculateSlotDeltaSeconds() {
    int slotSeconds = spec.getSecondsPerSlot(UInt64.ZERO);
    assertThat(dutyLogger.calculateSlotDeltaSeconds(UInt64.ZERO, UInt64.ZERO))
        .isEqualTo(UInt64.ZERO);
    assertThat(dutyLogger.calculateSlotDeltaSeconds(UInt64.ZERO, UInt64.ONE))
        .isEqualTo(UInt64.valueOf(slotSeconds));
    assertThat(dutyLogger.calculateSlotDeltaSeconds(UInt64.ONE, UInt64.valueOf(5)))
        .isEqualTo(UInt64.valueOf(slotSeconds).times(4));
  }

  @Test
  public void shouldThrowOnSlotDeltaIncorrectInput() {
    assertThatExceptionOfType(ArithmeticException.class)
        .isThrownBy(() -> dutyLogger.calculateSlotDeltaSeconds(UInt64.ONE, UInt64.ZERO));
  }

  @Test
  public void cacheShouldKeepLatestEpoch() {
    Map<UInt64, String> cache = DutyLogger.createCacheMap(1);
    cache.put(UInt64.ONE, "one");
    assertThat(cache.containsKey(UInt64.ONE)).isTrue();
    assertThat(cache.get(UInt64.ONE)).isEqualTo("one");
    cache.put(UInt64.ZERO, "zero");
    assertThat(cache.containsKey(UInt64.ZERO)).isFalse();
    assertThat(cache.containsKey(UInt64.ONE)).isTrue();
    cache.put(UInt64.valueOf(2), "two");
    assertThat(cache.containsKey(UInt64.ONE)).isFalse();
    assertThat(cache.containsKey(UInt64.valueOf(2))).isTrue();
  }

  @Test
  public void cacheShouldKeepLatestEpochs() {
    Map<UInt64, String> cache = DutyLogger.createCacheMap(2);
    cache.put(UInt64.ONE, "one");
    cache.put(UInt64.valueOf(2), "two");
    assertThat(cache.containsKey(UInt64.ONE)).isTrue();
    assertThat(cache.containsKey(UInt64.valueOf(2))).isTrue();
    cache.put(UInt64.ZERO, "zero");
    assertThat(cache.containsKey(UInt64.ZERO)).isFalse();
    assertThat(cache.containsKey(UInt64.ONE)).isTrue();
    assertThat(cache.containsKey(UInt64.valueOf(2))).isTrue();
    cache.put(UInt64.valueOf(3), "three");
    assertThat(cache.containsKey(UInt64.ONE)).isFalse();
    assertThat(cache.containsKey(UInt64.valueOf(2))).isTrue();
    assertThat(cache.containsKey(UInt64.valueOf(3))).isTrue();
  }

  static class SomeDutyLogger extends DutyLogger {
    public SomeDutyLogger(Spec spec) {
      super(spec);
    }

    @Override
    void onEpochStartSlot(UInt64 slot) {}

    @Override
    void onSlot(UInt64 slot) {}

    @Override
    void invalidate() {}
  }
}
