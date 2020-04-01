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

package tech.pegasys.artemis.storage.server.mapdb;

import com.google.common.primitives.UnsignedLong;
import java.io.IOException;
import java.util.Comparator;
import org.jetbrains.annotations.NotNull;
import org.mapdb.DataInput2;
import org.mapdb.DataOutput2;
import org.mapdb.serializer.SerializerEightByte;

public class UnsignedLongSerializer extends SerializerEightByte<UnsignedLong> {

  @Override
  protected UnsignedLong unpack(final long data) {
    return UnsignedLong.fromLongBits(data);
  }

  @Override
  protected long pack(final UnsignedLong value) {
    return value.longValue();
  }

  @Override
  public int valueArraySearch(final Object keys, final UnsignedLong key) {
    return valueArraySearch(keys, key, Comparator.naturalOrder());
  }

  @Override
  public void serialize(@NotNull final DataOutput2 out, @NotNull final UnsignedLong value)
      throws IOException {
    out.writeLong(value.longValue());
  }

  @Override
  public UnsignedLong deserialize(@NotNull final DataInput2 input, final int available)
      throws IOException {
    return UnsignedLong.fromLongBits(input.readLong());
  }
}
