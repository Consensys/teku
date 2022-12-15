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
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;

/**
 * This serializer is intended to be used as a Key so that it preserve slot ordering when we stream
 * data. This is useful for values that are always looked up by root and slot, giving us the ability
 * to quickly lookup most recent\oldest values by slot as well as perform pruning based on slot
 */
class SlotAndBlockRootKeySerializer implements KvStoreSerializer<SlotAndBlockRoot> {
  @Override
  public SlotAndBlockRoot deserialize(final byte[] data) {
    return new SlotAndBlockRoot(
        UInt64.fromLongBits(
            Longs.fromBytes(
                data[0], data[1], data[2], data[3], data[4], data[5], data[6], data[7])),
        Bytes32.wrap(data, 8));
  }

  @Override
  public byte[] serialize(final SlotAndBlockRoot value) {

    return Bytes.concatenate(
            Bytes.wrap(Longs.toByteArray(value.getSlot().longValue())), value.getBlockRoot())
        .toArrayUnsafe();
  }
}
