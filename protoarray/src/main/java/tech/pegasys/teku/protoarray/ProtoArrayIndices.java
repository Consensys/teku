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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;

public class ProtoArrayIndices {
  private final Map<Bytes32, Integer> rootIndices = new HashMap<>();

  public boolean contains(final Bytes32 root) {
    return rootIndices.containsKey(root);
  }

  public void add(final Bytes32 blockRoot, final int nodeIndex) {
    rootIndices.put(blockRoot, nodeIndex);
  }

  public Optional<Integer> get(final Bytes32 root) {
    return Optional.ofNullable(rootIndices.get(root));
  }

  public void remove(final Bytes32 root) {
    rootIndices.remove(root);
  }

  public void offsetIndexes(final int finalizedIndex) {
    rootIndices.replaceAll(
        (key, value) -> {
          int newIndex = value - finalizedIndex;
          checkState(newIndex >= 0, "ProtoArray: New array index less than 0.");
          return newIndex;
        });
  }

  public Map<Bytes32, Integer> getRootIndices() {
    return rootIndices;
  }
}
