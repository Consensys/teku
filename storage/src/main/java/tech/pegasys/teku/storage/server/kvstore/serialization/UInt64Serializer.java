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

package tech.pegasys.teku.storage.server.kvstore.serialization;

import com.google.common.primitives.Longs;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

class UInt64Serializer implements KvStoreSerializer<UInt64> {

  @Override
  public UInt64 deserialize(final byte[] data) {
    return UInt64.fromLongBits(Longs.fromByteArray(data));
  }

  @Override
  public byte[] serialize(final UInt64 value) {
    return Longs.toByteArray(value.longValue());
  }
}
