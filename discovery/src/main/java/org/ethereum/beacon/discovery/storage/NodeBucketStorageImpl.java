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

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.database.DataSource;
import org.ethereum.beacon.discovery.database.DataSourceList;
import org.ethereum.beacon.discovery.database.Database;
import org.ethereum.beacon.discovery.database.HoleyList;
import org.ethereum.beacon.discovery.format.SerializerFactory;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordInfo;
import org.ethereum.beacon.discovery.type.BytesValue;
import org.ethereum.beacon.discovery.util.Functions;

// import tech.pegasys.artemis.util.bytes.Bytes;
// import tech.pegasys.artemis.util.bytes.Bytes;

/**
 * Stores {@link NodeRecordInfo}'s in {@link NodeBucket}'s calculating index number of bucket as
 * {@link Functions#logDistance(Bytes, Bytes)} from homeNodeId and ignoring index above {@link
 * #MAXIMUM_BUCKET}
 */
public class NodeBucketStorageImpl implements NodeBucketStorage {
  public static final String NODE_BUCKET_STORAGE_NAME = "node-bucket-table";
  public static final int MAXIMUM_BUCKET = 256;
  private final HoleyList<NodeBucket> nodeBucketsTable;
  private final Bytes homeNodeId;

  public NodeBucketStorageImpl(
      Database database, SerializerFactory serializerFactory, NodeRecord homeNode) {
    DataSource<BytesValue, BytesValue> nodeBucketsSource =
        database.createStorage(NODE_BUCKET_STORAGE_NAME);
    this.nodeBucketsTable =
        new DataSourceList<>(
            nodeBucketsSource,
            serializerFactory.getSerializer(NodeBucket.class),
            serializerFactory.getDeserializer(NodeBucket.class));
    this.homeNodeId = homeNode.getNodeId();
    // Empty storage, saving home node
    if (!nodeBucketsTable.get(0).isPresent()) {
      NodeBucket zero = new NodeBucket();
      zero.put(NodeRecordInfo.createDefault(homeNode));
      nodeBucketsTable.put(0, zero);
    }
  }

  @Override
  public Optional<NodeBucket> get(int index) {
    return nodeBucketsTable.get(index);
  }

  @Override
  public void put(NodeRecordInfo nodeRecordInfo) {
    int logDistance = Functions.logDistance(homeNodeId, nodeRecordInfo.getNode().getNodeId());
    if (logDistance <= MAXIMUM_BUCKET) {
      Optional<NodeBucket> nodeBucketOpt = nodeBucketsTable.get(logDistance);
      if (nodeBucketOpt.isPresent()) {
        NodeBucket nodeBucket = nodeBucketOpt.get();
        boolean updated = nodeBucket.put(nodeRecordInfo);
        if (updated) {
          nodeBucketsTable.put(logDistance, nodeBucket);
        }
      } else {
        NodeBucket nodeBucket = new NodeBucket();
        nodeBucket.put(nodeRecordInfo);
        nodeBucketsTable.put(logDistance, nodeBucket);
      }
    }
  }

  @Override
  public void commit() {}
}
