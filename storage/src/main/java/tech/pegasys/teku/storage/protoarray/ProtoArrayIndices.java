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

package tech.pegasys.teku.storage.protoarray;

import static com.google.common.base.Preconditions.checkState;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;

public class ProtoArrayIndices {
  private final Object2IntMap<Bytes32> rootIndices = new Object2IntOpenHashMap<>();

  public boolean contains(final Bytes32 root) {
    return rootIndices.containsKey(root);
  }

  public void add(final Bytes32 blockRoot, final int nodeIndex) {
    rootIndices.put(blockRoot, nodeIndex);
  }

  public Optional<Integer> get(final Bytes32 root) {
    return rootIndices.containsKey(root) ? Optional.of(rootIndices.getInt(root)) : Optional.empty();
  }

  public void remove(final Bytes32 root) {
    rootIndices.removeInt(root);
  }

  public void offsetIndices(final int finalizedIndex) {
    rootIndices.replaceAll(
        (key, value) -> {
          int newIndex = value - finalizedIndex;
          checkState(newIndex >= 0, "ProtoArray: New array index less than 0.");
          return newIndex;
        });
  }

  public Object2IntMap<Bytes32> getRootIndices() {
    return rootIndices;
  }
}
