/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.storage.server.kvstore.serialization;

import com.google.common.primitives.Longs;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.util.SlotAndBlockRootAndBlobIndex;

/**
 * This serializer is intended to be used as a Key so that it preserve slot ordering when we stream
 * data. This is useful for values that are always looked up by root, slot and blobIndex, giving us
 * the ability to quickly lookup most recent\oldest values by slot as well as perform pruning based
 * on slot
 */
class SlotAndBlockRootAndBlobIndexKeySerializer
    implements KvStoreSerializer<SlotAndBlockRootAndBlobIndex> {
  static final int UINT64_SIZE = 64 / Byte.SIZE;
  static final int BLOB_INDEX_OFFSET = Bytes32.SIZE + UINT64_SIZE;

  @Override
  public SlotAndBlockRootAndBlobIndex deserialize(final byte[] data) {
    final UInt64 slot =
        UInt64.fromLongBits(
            Longs.fromBytes(
                data[0], data[1], data[2], data[3], data[4], data[5], data[6], data[7]));
    final Bytes rootBytes = Bytes.wrap(data, UINT64_SIZE, Bytes32.SIZE);
    final Bytes blobIndexBytes = Bytes.wrap(data, BLOB_INDEX_OFFSET, UINT64_SIZE);
    final UInt64 blobIndex =
        UInt64.fromLongBits(
            Longs.fromBytes(
                blobIndexBytes.get(0),
                blobIndexBytes.get(1),
                blobIndexBytes.get(2),
                blobIndexBytes.get(3),
                blobIndexBytes.get(4),
                blobIndexBytes.get(5),
                blobIndexBytes.get(6),
                blobIndexBytes.get(7)));
    return new SlotAndBlockRootAndBlobIndex(slot, Bytes32.wrap(rootBytes), blobIndex);
  }

  @Override
  public byte[] serialize(final SlotAndBlockRootAndBlobIndex value) {
    return Bytes.concatenate(
            Bytes.wrap(Longs.toByteArray(value.getSlot().longValue())),
            value.getBlockRoot(),
            Bytes.wrap(Longs.toByteArray(value.getBlobIndex().longValue())))
        .toArrayUnsafe();
  }
}
