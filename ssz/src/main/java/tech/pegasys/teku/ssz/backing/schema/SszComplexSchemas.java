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

package tech.pegasys.teku.ssz.backing.schema;

import com.google.common.base.Preconditions;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.ssz.backing.SszList;
import tech.pegasys.teku.ssz.backing.SszVector;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszBit;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.SszByte;
import tech.pegasys.teku.ssz.backing.view.SszUtils;

public class SszComplexSchemas {

  public static final SszByteVectorSchema BYTES_48_SCHEMA = new SszByteVectorSchema(48);
  public static final SszByteVectorSchema BYTES_96_SCHEMA = new SszByteVectorSchema(96);

  public static class SszByteListSchema extends SszListSchema<SszByte> {
    public SszByteListSchema(long maxLength) {
      super(SszPrimitiveSchemas.BYTE_SCHEMA, maxLength);
    }

    public SszList<SszByte> createList(Bytes bytes) {
      Preconditions.checkArgument(
          bytes.size() > getMaxLength(), "Bytes length exceeds List type maximum length ");
      return SszUtils.toSszByteList(this, bytes);
    }

    @Override
    public String toString() {
      return "ByteList[" + getMaxLength() + "]";
    }
  }

  public static class SszByteVectorSchema extends SszVectorSchema<SszByte, SszVector<SszByte>> {
    public SszByteVectorSchema(long maxLength) {
      super(SszPrimitiveSchemas.BYTE_SCHEMA, maxLength);
    }

    public SszVector<SszByte> createVector(Bytes bytes) {
      Preconditions.checkArgument(
          bytes.size() == getLength(), "Bytes length doesn't match Vector type length ");
      return SszUtils.toSszByteVector(this, bytes);
    }

    @Override
    public String toString() {
      return "Bytes" + getLength();
    }
  }

  public static class SszBitListSchema extends SszListSchema<SszBit> {
    public SszBitListSchema(long maxLength) {
      super(SszPrimitiveSchemas.BIT_SCHEMA, maxLength);
    }

    @Override
    public String toString() {
      return "BitList[" + getMaxLength() + "]";
    }
  }

  public static class SszBitVectorSchema extends SszVectorSchema<SszBit, SszVector<SszBit>> {
    public SszBitVectorSchema(long maxLength) {
      super(SszPrimitiveSchemas.BIT_SCHEMA, maxLength);
    }

    @Override
    public String toString() {
      return "BitVector[" + getLength() + "]";
    }
  }
}
