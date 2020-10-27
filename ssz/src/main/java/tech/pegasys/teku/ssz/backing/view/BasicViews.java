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
import tech.pegasys.teku.ssz.backing.type.BasicViewTypes;

/** Collection of basic view classes */
public class BasicViews {

  public static class BitView extends AbstractBasicView<Boolean, BitView> {
    private static final BitView TRUE_VIEW = new BitView(true);
    private static final BitView FALSE_VIEW = new BitView(false);

    public static BitView viewOf(boolean value) {
      return value ? TRUE_VIEW : FALSE_VIEW;
    }

    private BitView(Boolean value) {
      super(value, BasicViewTypes.BIT_TYPE);
    }
  }

  public static class ByteView extends AbstractBasicView<Byte, ByteView> {
    public ByteView(Byte value) {
      super(value, BasicViewTypes.BYTE_TYPE);
    }
  }

  public static class UInt64View extends AbstractBasicView<UInt64, UInt64View> {

    public static UInt64View fromLong(long val) {
      return new UInt64View(UInt64.fromLongBits(val));
    }

    public UInt64View(UInt64 val) {
      super(val, BasicViewTypes.UINT64_TYPE);
    }

    public long longValue() {
      return get().longValue();
    }
  }

  public static class Bytes4View extends AbstractBasicView<Bytes4, Bytes4View> {

    public Bytes4View(Bytes4 val) {
      super(val, BasicViewTypes.BYTES4_TYPE);
    }
  }

  public static class Bytes32View extends AbstractBasicView<Bytes32, Bytes32View> {

    public Bytes32View(Bytes32 val) {
      super(val, BasicViewTypes.BYTES32_TYPE);
    }
  }
}
