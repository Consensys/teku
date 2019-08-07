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

import static tech.pegasys.artemis.datastructures.Constants.BYTES_PER_LENGTH_OFFSET;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
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

    // System.out.println("Fixed Part Size: " + value.get_fixed_parts().size());
    // System.out.println("Var Part Size: " + value.get_variable_parts().size());
    for (Bytes fixedPart : value.get_fixed_parts()) {
      UnsignedLong fixedPartSize = UnsignedLong.valueOf(fixedPart.size());
      if (fixedPartSize.equals(UnsignedLong.ZERO)) {
        fixedPartSize = UnsignedLong.valueOf(4L);
      }
      fixedLengthSum = fixedLengthSum.plus(fixedPartSize);
    }

    variable_offsets.add(fixedLengthSum);
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
            SSZ.encodeUInt32(variable_offsets.get(interleavingIndex).longValue()));
      }
      ++interleavingIndex;
    }

    return Bytes.wrap(
        Bytes.concatenate(interleaved_values.toArray(new Bytes[0])),
        Bytes.concatenate(value.get_variable_parts().toArray(new Bytes[0])));
  }

  public static Bytes serializeFixedCompositeList(List<? extends SimpleOffsetSerializable> values) {
    return Bytes.fromHexString(
        values.stream()
            .map(item -> serialize(item).toHexString().substring(2))
            .collect(Collectors.joining()));
  }

  public static Bytes serializeVariableCompositeList(
      List<? extends SimpleOffsetSerializable> values) {
    List<Bytes> parts =
        values.stream().map(SimpleOffsetSerializer::serialize).collect(Collectors.toList());
    List<UnsignedLong> fixed_lengths = Collections.nCopies(values.size(), BYTES_PER_LENGTH_OFFSET);
    List<Bytes> variable_parts = new ArrayList<>();
    List<Bytes> fixed_parts = new ArrayList<>();
    UnsignedLong offset = UnsignedLong.ZERO;
    for (UnsignedLong length : fixed_lengths) {
      offset = offset.plus(length);
    }
    for (Bytes part : parts) {
      fixed_parts.add(SSZ.encodeUInt32(offset.longValue()));
      variable_parts.add(part);
      offset = offset.plus(UnsignedLong.valueOf(part.size()));
    }
    return Bytes.wrap(
        Bytes.concatenate(fixed_parts.toArray(new Bytes[0])),
        Bytes.concatenate(variable_parts.toArray(new Bytes[0])));
  }
}
