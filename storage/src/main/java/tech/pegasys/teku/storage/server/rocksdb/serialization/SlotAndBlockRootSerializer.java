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

package tech.pegasys.teku.storage.server.rocksdb.serialization;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;

class SlotAndBlockRootSerializer implements RocksDbSerializer<SlotAndBlockRoot> {
  @Override
  public SlotAndBlockRoot deserialize(final byte[] data) {
    return SSZ.decode(
        Bytes.of(data),
        reader -> {
          final UInt64 slot = UInt64.fromLongBits(reader.readUInt64());
          final Bytes32 blockRoot = Bytes32.wrap(reader.readFixedBytes(Bytes32.SIZE));
          return new SlotAndBlockRoot(slot, blockRoot);
        });
  }

  @Override
  public byte[] serialize(final SlotAndBlockRoot value) {
    Bytes bytes =
        SSZ.encode(
            writer -> {
              writer.writeUInt64(value.getSlot().longValue());
              writer.writeFixedBytes(value.getBlockRoot());
            });
    return bytes.toArrayUnsafe();
  }
}
