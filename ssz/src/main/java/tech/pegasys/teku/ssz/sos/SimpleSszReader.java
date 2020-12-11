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

package tech.pegasys.teku.ssz.sos;

import org.apache.tuweni.bytes.Bytes;

class SimpleSszReader implements SszReader {

  private final Bytes bytes;
  private int offset = 0;

  public SimpleSszReader(Bytes bytes) {
    this.bytes = bytes;
  }

  @Override
  public int getAvailableBytes() {
    return bytes.size() - offset;
  }

  @Override
  public SszReader slice(int size) {
    checkIfAvailable(size);
    SimpleSszReader ret = new SimpleSszReader(bytes.slice(offset, size));
    offset += size;
    return ret;
  }

  @Override
  public Bytes read(int length) {
    checkIfAvailable(length);
    Bytes ret = bytes.slice(offset, length);
    offset += length;
    return ret;
  }

  private void checkIfAvailable(int size) {
    if (getAvailableBytes() < size) {
      throw new SSZDeserializeException("Invalid SSZ: trying to read more bytes than available");
    }
  }

  @Override
  public void close() {
    if (getAvailableBytes() > 0) {
      throw new SSZDeserializeException("Invalid SSZ: unread bytes remain: " + getAvailableBytes());
    }
  }
}
