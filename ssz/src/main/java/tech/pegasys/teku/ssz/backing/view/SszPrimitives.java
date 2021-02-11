/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.ssz.backing.view;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.Bytes4;
import tech.pegasys.teku.ssz.backing.SszPrimitive;
import tech.pegasys.teku.ssz.backing.schema.SszPrimitiveSchemas;

/** Collection of {@link SszPrimitive} classes */
public class SszPrimitives {

  public static class SszBit extends AbstractSszPrimitive<Boolean, SszBit> {
    private static final SszBit TRUE_VIEW = new SszBit(true);
    private static final SszBit FALSE_VIEW = new SszBit(false);

    public static SszBit viewOf(boolean value) {
      return value ? TRUE_VIEW : FALSE_VIEW;
    }

    private SszBit(Boolean value) {
      super(value, SszPrimitiveSchemas.BIT_SCHEMA);
    }

    @Override
    @SuppressWarnings("ReferenceEquality")
    public String toString() {
      return this == TRUE_VIEW ? "1" : "0";
    }
  }

  public static class SszByte extends AbstractSszPrimitive<Byte, SszByte> {
    public SszByte(Byte value) {
      super(value, SszPrimitiveSchemas.BYTE_SCHEMA);
    }
  }

  public static class SszUInt64 extends AbstractSszPrimitive<UInt64, SszUInt64> {

    public static SszUInt64 fromLong(long val) {
      return new SszUInt64(UInt64.fromLongBits(val));
    }

    public SszUInt64(UInt64 val) {
      super(val, SszPrimitiveSchemas.UINT64_SCHEMA);
    }

    public long longValue() {
      return get().longValue();
    }
  }

  public static class SszBytes4 extends AbstractSszPrimitive<Bytes4, SszBytes4> {

    public SszBytes4(Bytes4 val) {
      super(val, SszPrimitiveSchemas.BYTES4_SCHEMA);
    }
  }

  public static class SszBytes32 extends AbstractSszPrimitive<Bytes32, SszBytes32> {

    public SszBytes32(Bytes32 val) {
      super(val, SszPrimitiveSchemas.BYTES32_SCHEMA);
    }
  }
}
