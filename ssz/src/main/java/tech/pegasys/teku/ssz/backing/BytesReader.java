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

package tech.pegasys.teku.ssz.backing;

import org.apache.tuweni.bytes.Bytes;

public interface BytesReader {

  static BytesReader fromBytes(Bytes bytes) {
    return new SimpleBytesReader(bytes);
  }

  int getAvailableBytes();

  BytesReader slice(int size);

  Bytes read(int length);
}

class SimpleBytesReader implements BytesReader {
  Bytes bytes;
  int offset = 0;

  public SimpleBytesReader(Bytes bytes) {
    this.bytes = bytes;
  }

  @Override
  public int getAvailableBytes() {
    return bytes.size() - offset;
  }

  @Override
  public BytesReader slice(int size) {
    checkOffset(offset + size);
    SimpleBytesReader ret = new SimpleBytesReader(bytes.slice(offset, size));
    offset += size;
    return ret;
  }

  @Override
  public Bytes read(int length) {
    checkOffset(offset + length);
    Bytes ret = bytes.slice(offset, length);
    offset += length;
    return ret;
  }

  private void checkOffset(int offset) {
    if (offset > bytes.size()) {
      throw new IndexOutOfBoundsException();
    }
  }
}
