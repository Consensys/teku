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

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;

/** Unordered storage for arbitrary byte data. Byte data is expected to be 1-20kb in size. */
public interface BlobStorage extends AutoCloseable {
  /** Load the data with the specified ID. */
  Optional<Bytes> load(Bytes32 id);

  BlobTransaction startTransaction();

  @Override
  void close();

  interface BlobTransaction extends AutoCloseable {
    /** Store the specified data. An arbitrary ID is assigned to the data and returned. */
    Bytes32 store(Bytes32 id, SimpleOffsetSerializable data);

    /** Delete the data identified by the specified ID. Does nothing if the data does not exist. */
    void delete(Bytes32 id);

    /** Persist the specified changes. */
    void commit();

    @Override
    void close();
  }
}
