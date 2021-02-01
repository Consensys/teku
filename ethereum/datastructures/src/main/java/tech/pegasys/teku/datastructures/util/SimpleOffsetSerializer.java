/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.teku.datastructures.util;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.ssz.backing.ViewRead;
import tech.pegasys.teku.ssz.backing.type.ViewType;
import tech.pegasys.teku.ssz.sos.SszLengthBounds;

public class SimpleOffsetSerializer {

  public static void setConstants() {
    SpecDependent.resetAll();
  }

  static {
    setConstants();
  }

  public static Bytes serialize(Object value) {
    if (value instanceof ViewRead) {
      return ((ViewRead) value).sszSerialize();
    }
    throw new UnsupportedOperationException();
  }

  @SuppressWarnings("unchecked")
  public static <T> T deserialize(Bytes bytes, Class<T> classInfo) {
    Optional<ViewType<?>> maybeViewType = ViewType.getType(classInfo);
    if (maybeViewType.isPresent()) {
      return (T) maybeViewType.get().sszDeserialize(bytes);
    } else {
      throw new UnsupportedOperationException();
    }
  }

  public static <T> Optional<LengthBounds> getLengthBounds(final Class<T> type) {
    Optional<ViewType<?>> maybeViewType = ViewType.getType(type);
    if (maybeViewType.isPresent()) {
      SszLengthBounds lengthBounds = maybeViewType.get().getSszLengthBounds();
      return Optional.of(new LengthBounds(lengthBounds.getMinBytes(), lengthBounds.getMaxBytes()));
    } else {
      throw new UnsupportedOperationException();
    }
  }
}
