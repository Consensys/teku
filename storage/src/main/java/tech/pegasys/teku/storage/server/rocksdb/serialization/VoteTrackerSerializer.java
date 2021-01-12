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
import tech.pegasys.teku.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class VoteTrackerSerializer implements RocksDbSerializer<VoteTracker> {
  @Override
  public VoteTracker deserialize(final byte[] data) {
    return SSZ.decode(
        Bytes.of(data),
        reader -> {
          final Bytes32 currentRoot = Bytes32.wrap(reader.readFixedBytes(Bytes32.SIZE));
          final Bytes32 nextRoot = Bytes32.wrap(reader.readFixedBytes(Bytes32.SIZE));
          final UInt64 nextEpoch = UInt64.fromLongBits(reader.readUInt64());
          return new VoteTracker(currentRoot, nextRoot, nextEpoch);
        });
  }

  @Override
  public byte[] serialize(final VoteTracker value) {
    Bytes bytes =
        SSZ.encode(
            writer -> {
              writer.writeFixedBytes(value.getCurrentRoot());
              writer.writeFixedBytes(value.getNextRoot());
              writer.writeUInt64(value.getNextEpoch().longValue());
            });
    return bytes.toArrayUnsafe();
  }
}
