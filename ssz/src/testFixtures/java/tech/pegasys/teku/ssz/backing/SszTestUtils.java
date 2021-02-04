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

package tech.pegasys.teku.ssz.backing;

import java.util.List;
import java.util.stream.Collectors;
import tech.pegasys.teku.ssz.backing.type.ContainerViewType;
import tech.pegasys.teku.ssz.backing.type.VectorViewType;

public class SszTestUtils {

  public static List<Integer> getVectorLengths(ContainerViewType<?> containerViewType) {
    return containerViewType.getChildTypes().stream()
        .filter(t -> t instanceof VectorViewType)
        .map(t -> (VectorViewType<?>) t)
        .map(VectorViewType::getLength)
        .collect(Collectors.toList());
  }

  /** Compares two views by their getters recursively (if views are composite) */
  public static boolean equalsByGetters(ViewRead v1, ViewRead v2) {
    if (!v1.getType().equals(v2.getType())) {
      return false;
    }
    if (v1 instanceof CompositeViewRead) {
      CompositeViewRead<?> c1 = (CompositeViewRead<?>) v1;
      CompositeViewRead<?> c2 = (CompositeViewRead<?>) v2;
      if (c1.size() != c2.size()) {
        return false;
      }
      for (int i = 0; i < c1.size(); i++) {
        if (c1.get(i) instanceof ViewRead) {
          if (!equalsByGetters((ViewRead) c1.get(i), (ViewRead) c2.get(i))) {
            return false;
          }
        } else {
          if (!c1.equals(c2)) {
            return false;
          }
        }
      }
      return true;
    } else {
      return v1.equals(v2);
    }
  }
}
