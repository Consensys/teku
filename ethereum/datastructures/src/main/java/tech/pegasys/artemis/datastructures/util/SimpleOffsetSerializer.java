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

package tech.pegasys.artemis.datastructures.util;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.artemis.util.sos.SimpleOffsetSerializable;

public class SimpleOffsetSerializer {

  public static Bytes serialize(SimpleOffsetSerializable value) {
    // TODO assert sum(fixed_lengths + variable_lengths) < 2**(BYTES_PER_LENGTH_OFFSET *
    // BITS_PER_BYTE)
    // List<UnsignedLong> variable_lengths = new ArrayList<>();
    List<UnsignedLong> variable_offsets = new ArrayList<>();
    List<Bytes> interleaved_values = new ArrayList<>();
    UnsignedLong fixedLengthSum = UnsignedLong.ZERO;
    UnsignedLong varLengthSum = UnsignedLong.ZERO;

    for (Bytes fixedPart : value.get_fixed_parts()) {
      UnsignedLong fixedPartSize = UnsignedLong.valueOf(fixedPart.size());
      fixedLengthSum = fixedLengthSum.plus(fixedPartSize);
    }

    for (Bytes varPart : value.get_variable_parts()) {
      UnsignedLong varPartSize = UnsignedLong.valueOf(varPart.size());
      varLengthSum = varLengthSum.plus(varPartSize);
      variable_offsets.add(fixedLengthSum.plus(varLengthSum));
    }

    int interleavingIndex = 0;
    for (Bytes element : value.get_fixed_parts()) {
      if (!element.equals(Bytes.EMPTY)) {
        interleaved_values.add(element);
      } else {
        interleaved_values.add(
            SSZ.encodeUInt64(variable_offsets.get(interleavingIndex).longValue()));
      }
      ++interleavingIndex;
    }

    return Bytes.wrap(
        Bytes.concatenate(interleaved_values.toArray(new Bytes[0])),
        Bytes.concatenate(value.get_variable_parts().toArray(new Bytes[0])));
  }
}
