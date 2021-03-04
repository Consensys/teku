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

package tech.pegasys.teku.protoarray;

import static com.google.common.base.Preconditions.checkState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;

public class ProtoArrayIndices {
  private final Map<Bytes32, List<Integer>> rootIndices = new HashMap<>();

  public boolean contains(final Bytes32 root) {
    return rootIndices.containsKey(root);
  }

  public Optional<Integer> indexOf(final Bytes32 root) {
    if (rootIndices.containsKey(root)) {
      return Optional.of(rootIndices.get(root).get(0));
    }
    return Optional.empty();
  }

  public void add(final Bytes32 blockRoot, final int nodeIndex) {
    final ArrayList<Integer> indexes = new ArrayList<>();
    indexes.add(nodeIndex);
    rootIndices.put(blockRoot, indexes);
  }

  public Optional<Integer> getFirst(final Bytes32 justifiedRoot) {
    if (!rootIndices.containsKey(justifiedRoot)) {
      return Optional.empty();
    }
    return Optional.of(rootIndices.get(justifiedRoot).get(0));
  }

  public void remove(final Bytes32 root) {
    rootIndices.remove(root);
  }

  public void offsetIndexes(final int finalizedIndex) {
    rootIndices.replaceAll(
        (key, list) -> {
          final List<Integer> newIndexes =
              list.stream().map(i -> i - finalizedIndex).collect(Collectors.toList());
          checkState(newIndexes.get(0) >= 0, "ProtoArray: New array index less than 0.");
          return newIndexes;
        });
  }

  public Map<Bytes32, Integer> getBlockIndices() {
    final Map<Bytes32, Integer> indices = new HashMap<>();
    rootIndices.forEach((key, value) -> indices.put(key, value.get(0)));
    return indices;
  }
}
