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
import org.ethereum.beacon.ssz.access.SSZField;
import org.ethereum.beacon.ssz.type.SSZType;
import tech.pegasys.artemis.util.bytes.Bytes1;
import tech.pegasys.artemis.util.bytes.Bytes32;
import tech.pegasys.artemis.util.bytes.Bytes4;
import tech.pegasys.artemis.util.bytes.Bytes48;
import tech.pegasys.artemis.util.bytes.Bytes96;
import tech.pegasys.artemis.util.bytes.BytesValue;

public class BytesValueAccessor extends AbstractListAccessor {

  @Override
  public int getChildrenCount(Object value) {
    return ((BytesValue) value).size();
  }

  @Override
  public Object getChildValue(Object value, int idx) {
    return ((BytesValue) value).get(idx);
  }

  @Override
  public SSZField getListElementType(SSZField listTypeDescriptor) {
    return new SSZField(byte.class);
  }

  @Override
  public ListInstanceBuilder createInstanceBuilder(SSZType sszType) {
    return new SimpleInstanceBuilder() {
      @Override
      protected Object buildImpl(List<Object> children) {
        byte[] vals = new byte[children.size()];
        for (int i = 0; i < children.size(); i++) {
          vals[i] = ((Number) children.get(i)).byteValue();
        }
        BytesValue value = BytesValue.wrap(vals);

        // Bytes1 .. Bytes96 vectors
        if (sszType.getSize() == 1) {
          return Bytes1.wrap(value.extractArray());
        } else if (sszType.getSize() == 4) {
          return Bytes4.wrap(value.extractArray());
        } else if (sszType.getSize() == 32) {
          return Bytes32.wrap(value.extractArray());
        } else if (sszType.getSize() == 48) {
          return Bytes48.wrap(value.extractArray());
        } else if (sszType.getSize() == 96) {
          return Bytes96.wrap(value.extractArray());
        }

        return value;
      }
    };
  }

  @Override
  public boolean isSupported(SSZField field) {
    return BytesValue.class.isAssignableFrom(field.getRawClass());
  }
}
