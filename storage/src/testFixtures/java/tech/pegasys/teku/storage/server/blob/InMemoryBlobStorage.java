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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.util.SimpleOffsetSerializer;
import tech.pegasys.teku.ssz.sos.SimpleOffsetSerializable;

public class InMemoryBlobStorage implements BlobStorage {
  private final Map<Bytes32, Bytes> blobs = new HashMap<>();

  @Override
  public Optional<Bytes> load(final Bytes32 id) {
    return Optional.ofNullable(blobs.get(id));
  }

  @Override
  public BlobTransaction startTransaction() {
    return new Transaction();
  }

  @Override
  public void close() {}

  private class Transaction implements BlobTransaction {
    private final Map<Bytes32, Bytes> added = new HashMap<>();
    private final Set<Bytes32> deleted = new HashSet<>();

    @Override
    public Bytes32 store(final Bytes32 id, final SimpleOffsetSerializable data) {
      added.put(id, SimpleOffsetSerializer.serialize(data));
      deleted.remove(id);
      return id;
    }

    @Override
    public void delete(final Bytes32 id) {
      added.remove(id);
      deleted.add(id);
    }

    @Override
    public void commit() {
      blobs.putAll(added);
      deleted.forEach(blobs::remove);
    }

    @Override
    public void close() {}
  }
}
