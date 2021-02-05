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
import tech.pegasys.teku.ssz.backing.schema.SszContainerSchema;
import tech.pegasys.teku.ssz.backing.schema.SszVectorSchema;

public class SszTestUtils {

  public static List<Integer> getVectorLengths(SszContainerSchema<?> sszContainerSchema) {
    return sszContainerSchema.getChildSchemas().stream()
        .filter(t -> t instanceof SszVectorSchema)
        .map(t -> (SszVectorSchema<?>) t)
        .map(SszVectorSchema::getLength)
        .collect(Collectors.toList());
  }

  /** Compares two views by their getters recursively (if views are composite) */
  public static boolean equalsByGetters(SszData v1, SszData v2) {
    if (!v1.getSchema().equals(v2.getSchema())) {
      return false;
    }
    if (v1 instanceof SszComposite) {
      SszComposite<?> c1 = (SszComposite<?>) v1;
      SszComposite<?> c2 = (SszComposite<?>) v2;
      if (c1.size() != c2.size()) {
        return false;
      }
      for (int i = 0; i < c1.size(); i++) {
        if (c1.get(i) instanceof SszData) {
          if (!equalsByGetters((SszData) c1.get(i), (SszData) c2.get(i))) {
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
