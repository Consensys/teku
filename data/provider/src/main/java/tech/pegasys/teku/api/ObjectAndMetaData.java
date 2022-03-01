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

package tech.pegasys.teku.api;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import tech.pegasys.teku.spec.SpecMilestone;

public class ObjectAndMetaData<T> {

  protected final T data;
  private final SpecMilestone milestone;
  private final boolean executionOptimistic;

  public ObjectAndMetaData(
      final T data, final SpecMilestone milestone, final boolean executionOptimistic) {
    this.data = data;
    this.milestone = milestone;
    this.executionOptimistic = executionOptimistic;
  }

  public T getData() {
    return data;
  }

  public SpecMilestone getMilestone() {
    return milestone;
  }

  public boolean isExecutionOptimistic() {
    return executionOptimistic;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ObjectAndMetaData<?> that = (ObjectAndMetaData<?>) o;
    return executionOptimistic == that.executionOptimistic
        && Objects.equals(data, that.data)
        && milestone == that.milestone;
  }

  @Override
  public int hashCode() {
    return Objects.hash(data, milestone, executionOptimistic);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("data", data)
        .add("milestone", milestone)
        .add("executionOptimistic", executionOptimistic)
        .toString();
  }
}
