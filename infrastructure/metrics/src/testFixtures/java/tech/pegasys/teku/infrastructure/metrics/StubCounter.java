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

package tech.pegasys.teku.infrastructure.metrics;

import static java.util.Arrays.asList;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;

public class StubCounter implements LabelledMetric<Counter> {
  private Map<List<String>, UnlabelledCounter> values = new ConcurrentHashMap<>();

  @Override
  public Counter labels(final String... labels) {
    return values.computeIfAbsent(asList(labels), __ -> new UnlabelledCounter());
  }

  public long getValue(final String... labels) {
    return Optional.ofNullable(values.get(asList(labels)))
        .map(UnlabelledCounter::getValue)
        .orElse(0L);
  }

  private static class UnlabelledCounter implements Counter {
    private final AtomicLong value = new AtomicLong();

    @Override
    public void inc() {
      value.incrementAndGet();
    }

    @Override
    public void inc(final long amount) {
      value.updateAndGet(value -> value + amount);
    }

    public long getValue() {
      return value.get();
    }
  }
}
