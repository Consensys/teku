/*
 * Copyright 2022 ConsenSys AG.
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

package tech.pegasys.teku.test.data.publisher;

import tech.pegasys.teku.data.publisher.MetricsPublisherSource;

public class StubMetricsPublisherSource implements MetricsPublisherSource {
  public final long cpuSecondsTotal;
  public final long memoryProcessBytes;
  public final int validatorsTotal;
  public final int validatorsActive;

  public StubMetricsPublisherSource(
      final long cpuSecondsTotal,
      final long memoryProcessBytes,
      final int validatorsTotal,
      final int validatorsActive) {
    this.cpuSecondsTotal = cpuSecondsTotal;
    this.memoryProcessBytes = memoryProcessBytes;
    this.validatorsTotal = validatorsTotal;
    this.validatorsActive = validatorsActive;
  }

  @Override
  public long getCpuSecondsTotal() {
    return cpuSecondsTotal;
  }

  @Override
  public long getMemoryProcessBytes() {
    return memoryProcessBytes;
  }

  @Override
  public int getValidatorsTotal() {
    return validatorsTotal;
  }

  @Override
  public int getValidatorsActive() {
    return validatorsActive;
  }
}
