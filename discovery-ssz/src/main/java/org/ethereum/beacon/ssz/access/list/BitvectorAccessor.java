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

package org.ethereum.beacon.ssz.access.list;

import java.util.List;
import org.ethereum.beacon.ssz.SSZDeserializeException;
import org.ethereum.beacon.ssz.access.SSZField;
import org.ethereum.beacon.ssz.type.SSZType;
import org.ethereum.beacon.ssz.type.list.SSZBitListType;
import tech.pegasys.artemis.util.bytes.MutableBytesValue;
import tech.pegasys.artemis.util.collections.Bitvector;

/** {@link Bitvector} accessor */
public class BitvectorAccessor extends BitlistAccessor {

  @Override
  public Object getChildValue(Object value, int idx) {
    Bitvector bitvector = ((Bitvector) value);
    return bitvector.getArrayUnsafe()[idx];
  }

  @Override
  public ListInstanceBuilder createInstanceBuilder(SSZType type) {
    return new SimpleInstanceBuilder() {
      @Override
      protected Object buildImpl(List<Object> children) {
        MutableBytesValue blank = MutableBytesValue.create(children.size());
        for (int i = 0; i < children.size(); i++) {
          blank.set(i, ((Integer) children.get(i)).byteValue());
        }

        try {
          return Bitvector.of(((SSZBitListType) type).getBitSize(), blank.copy());
        } catch (IllegalArgumentException ex) {
          throw new SSZDeserializeException(
              "Failed to create Bitvector instance from input data", ex);
        }
      }
    };
  }

  @Override
  public boolean isSupported(SSZField field) {
    return Bitvector.class.isAssignableFrom(field.getRawClass());
  }
}
