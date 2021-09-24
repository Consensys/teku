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

package tech.pegasys.teku.data.publisher;

import java.util.Objects;

public abstract class BaseMetricData {

  public final int version;
  public final long timestamp;
  public final String process;

  public BaseMetricData(int version, long timestamp, String process) {
    this.version = version;
    this.timestamp = timestamp;
    this.process = process;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    BaseMetricData that = (BaseMetricData) o;
    return version == that.version && timestamp == that.timestamp && process.equals(that.process);
  }

  @Override
  public int hashCode() {
    return Objects.hash(version, timestamp, process);
  }
}
