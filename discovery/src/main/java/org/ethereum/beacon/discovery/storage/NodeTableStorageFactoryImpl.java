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
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.Database;
import org.ethereum.beacon.discovery.NodeRecordInfo;
import org.ethereum.beacon.discovery.SerializerFactory;
import org.ethereum.beacon.discovery.enr.NodeRecord;

// import tech.pegasys.artemis.util.uint.UInt64;

public class NodeTableStorageFactoryImpl implements NodeTableStorageFactory {

  private boolean isStorageEmpty(NodeTableStorage nodeTableStorage) {
    return nodeTableStorage.get().getHomeNode() == null;
  }

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
  @Override
  public NodeTableStorage createTable(
      Database database,
      SerializerFactory serializerFactory,
      Function<UInt64, NodeRecord> homeNodeProvider,
      Supplier<List<NodeRecord>> bootNodesSupplier) {
    NodeTableStorage nodeTableStorage = new NodeTableStorageImpl(database, serializerFactory);

    // Init storage with boot nodes if its empty
    if (isStorageEmpty(nodeTableStorage)) {
      bootNodesSupplier
          .get()
          .forEach(
              nodeRecord -> {
                if (!(nodeRecord instanceof NodeRecord)) {
                  throw new RuntimeException("Only V4 node records are supported as boot nodes");
                }
                nodeRecord.verify();
                NodeRecordInfo nodeRecordInfo = NodeRecordInfo.createDefault(nodeRecord);
                nodeTableStorage.get().save(nodeRecordInfo);
              });
    }
    // Rewrite home node with updated sequence number on init
    UInt64 oldSeq =
        nodeTableStorage
            .getHomeNodeSource()
            .get()
            .map(nr -> nr.getNode().getSeq())
            .orElse(UInt64.ZERO);
    NodeRecord updatedHomeNodeRecord = homeNodeProvider.apply(oldSeq);
    updatedHomeNodeRecord.verify();
    nodeTableStorage.getHomeNodeSource().set(NodeRecordInfo.createDefault(updatedHomeNodeRecord));

    return nodeTableStorage;
  }

  @Override
  public NodeBucketStorage createBucketStorage(
      Database database, SerializerFactory serializerFactory, NodeRecord homeNode) {
    return new NodeBucketStorageImpl(database, serializerFactory, homeNode);
  }
}
