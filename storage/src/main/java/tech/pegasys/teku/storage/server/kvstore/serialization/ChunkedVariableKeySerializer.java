/*
 * Copyright Consensys Software Inc., 2022
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

import static com.google.common.base.Preconditions.checkArgument;

import org.apache.tuweni.bytes.Bytes;

/**
 * This serializer is intended to be used as a Key so that it preserve slot ordering when we stream
 * data. This is useful for values that are always looked up by root and slot, giving us the ability
 * to quickly lookup most recent\oldest values by slot as well as perform pruning based on slot
 */
public class ChunkedVariableKeySerializer
    implements KvStoreSerializer<ChunkedVariableKeySerializer.IdAndChunkKey> {
  static final int ID_KEY_SIZE = 1;

  // we support only 256 chunks
  static final int CHUNK_KEY_SIZE = 1;

  static final int ID_KEY_OFFSET = 0;
  static final int CHUNK_KEY_OFFSET = ID_KEY_OFFSET + ID_KEY_SIZE;
  static final int DATA_SIZE = CHUNK_KEY_OFFSET + CHUNK_KEY_SIZE;

  @Override
  public IdAndChunkKey deserialize(final byte[] data) {
    checkArgument(data.length == DATA_SIZE);
    return IdAndChunkKey.fromBytes(Bytes.wrap(data));
  }

  @Override
  public byte[] serialize(final IdAndChunkKey value) {
    return value.toBytes().toArrayUnsafe();
  }

  public record IdAndChunkKey(Bytes id, Bytes chunkKey) {
    public static IdAndChunkKey fromBytes(final Bytes bytes) {
      return new IdAndChunkKey(
          bytes.slice(0, ID_KEY_SIZE), bytes.slice(ID_KEY_SIZE, CHUNK_KEY_SIZE));
    }

    public Bytes toBytes() {
      return Bytes.concatenate(id, chunkKey);
    }
  }
}
