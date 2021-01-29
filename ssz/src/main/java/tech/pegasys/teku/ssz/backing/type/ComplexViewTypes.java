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

package tech.pegasys.teku.ssz.backing.type;

import com.google.common.base.Preconditions;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.ssz.backing.ListViewRead;
import tech.pegasys.teku.ssz.backing.VectorViewRead;
import tech.pegasys.teku.ssz.backing.view.BasicViews.BitView;
import tech.pegasys.teku.ssz.backing.view.BasicViews.ByteView;
import tech.pegasys.teku.ssz.backing.view.ViewUtils;

public class ComplexViewTypes {

  public static final ByteVectorType BYTES_48_TYPE = new ByteVectorType(48);
  public static final ByteVectorType BYTES_96_TYPE = new ByteVectorType(96);

  public static class ByteListType extends ListViewType<ByteView> {
    public ByteListType(long maxLength) {
      super(BasicViewTypes.BYTE_TYPE, maxLength);
    }

    public ListViewRead<ByteView> createList(Bytes bytes) {
      Preconditions.checkArgument(
          bytes.size() > getMaxLength(), "Bytes length exceeds List type maximum length ");
      return ViewUtils.createListFromBytes(this, bytes);
    }
  }

  public static class ByteVectorType extends VectorViewType<ByteView> {
    public ByteVectorType(long maxLength) {
      super(BasicViewTypes.BYTE_TYPE, maxLength);
    }

    public VectorViewRead<ByteView> createVector(Bytes bytes) {
      Preconditions.checkArgument(
          bytes.size() == getLength(), "Bytes length doesn't match Vector type length ");
      return ViewUtils.createVectorFromBytes(this, bytes);
    }
  }

  public static class BitListType extends ListViewType<BitView> {
    public BitListType(long maxLength) {
      super(BasicViewTypes.BIT_TYPE, maxLength);
    }
  }

  public static class BitVectorType extends VectorViewType<BitView> {
    public BitVectorType(long maxLength) {
      super(BasicViewTypes.BIT_TYPE, maxLength);
    }
  }
}
