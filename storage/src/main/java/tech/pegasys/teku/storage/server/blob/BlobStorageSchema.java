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

package tech.pegasys.teku.storage.server.blob;

import static tech.pegasys.teku.storage.server.rocksdb.serialization.RocksDbSerializer.BYTES32_SERIALIZER;
import static tech.pegasys.teku.storage.server.rocksdb.serialization.RocksDbSerializer.BYTES_SERIALIZER;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.storage.server.rocksdb.schema.RocksDbColumn;
import tech.pegasys.teku.storage.server.rocksdb.schema.Schema;

public interface BlobStorageSchema extends Schema {

  RocksDbColumn<Bytes32, Bytes> BLOBS =
      RocksDbColumn.create(1, BYTES32_SERIALIZER, BYTES_SERIALIZER);
}
