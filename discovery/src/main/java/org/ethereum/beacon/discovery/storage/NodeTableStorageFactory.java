/*
 * Copyright 2019 ConsenSys AG.
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

package org.ethereum.beacon.discovery.storage;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import org.ethereum.beacon.chain.storage.impl.SerializerFactory;
import org.ethereum.beacon.db.Database;
import org.ethereum.beacon.discovery.enr.NodeRecord;
import tech.pegasys.artemis.util.uint.UInt64;

public interface NodeTableStorageFactory {
  /**
   * Creates storage for nodes table
   *
   * @param database Database
   * @param serializerFactory Serializer factory
   * @param homeNodeProvider Home node provider, accepts old sequence number of home node, usually
   *     sequence number is increased by 1 on each restart and ENR is signed with new sequence
   *     number
   * @param bootNodesSupplier boot nodes provider
   * @return {@link NodeTableStorage} from `database` but if it doesn't exist, creates new one with
   *     home node provided by `homeNodeSupplier` and boot nodes provided with `bootNodesSupplier`.
   *     Uses `serializerFactory` for node records serialization.
   */
  NodeTableStorage createTable(
      Database database,
      SerializerFactory serializerFactory,
      Function<UInt64, NodeRecord> homeNodeProvider,
      Supplier<List<NodeRecord>> bootNodesSupplier);

  NodeBucketStorage createBucketStorage(
      Database database, SerializerFactory serializerFactory, NodeRecord homeNode);
}
