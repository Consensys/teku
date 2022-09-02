/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.api.migrated;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import tech.pegasys.teku.spec.datastructures.metadata.BlockAndMetaData;

public class BlockHeadersResponse {
  private final boolean executionOptimistic;
  private final List<BlockHeaderData> data;

  public BlockHeadersResponse(final List<BlockHeaderData> data, final Boolean executionOptimistic) {
    this.executionOptimistic = executionOptimistic;
    this.data = data;
  }

  public BlockHeadersResponse(
      final boolean executionOptimistic, final List<BlockAndMetaData> data) {
    this.executionOptimistic = executionOptimistic;
    this.data = data.stream().map(BlockHeaderData::new).collect(Collectors.toList());
  }

  public boolean isExecutionOptimistic() {
    return executionOptimistic;
  }

  public List<BlockHeaderData> getData() {
    return Collections.unmodifiableList(data);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    BlockHeadersResponse that = (BlockHeadersResponse) o;
    return executionOptimistic == that.executionOptimistic && Objects.equals(data, that.data);
  }

  @Override
  public int hashCode() {
    return Objects.hash(executionOptimistic, data);
  }

  @Override
  public String toString() {
    return "BlockHeadersResponse{"
        + "executionOptimistic="
        + executionOptimistic
        + ", data="
        + data
        + '}';
  }
}
