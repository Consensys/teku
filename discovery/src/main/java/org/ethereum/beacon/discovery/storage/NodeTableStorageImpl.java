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

import static org.ethereum.beacon.discovery.util.CryptoUtil.sha256;

import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.database.CodecSource;
import org.ethereum.beacon.discovery.database.DataSource;
import org.ethereum.beacon.discovery.database.DataSourceList;
import org.ethereum.beacon.discovery.database.Database;
import org.ethereum.beacon.discovery.database.HoleyList;
import org.ethereum.beacon.discovery.database.SingleValueSource;
import org.ethereum.beacon.discovery.format.SerializerFactory;
import org.ethereum.beacon.discovery.schema.NodeRecordInfo;

/** Creates NodeTableStorage containing NodeTable with indexes */
public class NodeTableStorageImpl implements NodeTableStorage {

  public static final String NODE_TABLE_STORAGE_NAME = "node-table";
  public static final String INDEXES_STORAGE_NAME = "node-table-index";
  private static final Bytes HOME_NODE_KEY = sha256(Bytes.wrap("HOME_NODE".getBytes()));
  private final DataSource<Bytes, Bytes> nodeTableSource;
  private final DataSource<Bytes, Bytes> nodeIndexesSource;
  private final SingleValueSource<NodeRecordInfo> homeNodeSource;
  private final NodeTable nodeTable;

  public NodeTableStorageImpl(Database database, SerializerFactory serializerFactory) {
    DataSource<Bytes, Bytes> nodeTableSource = database.createStorage(NODE_TABLE_STORAGE_NAME);
    this.nodeTableSource = nodeTableSource;
    DataSource<Bytes, Bytes> nodeIndexesSource = database.createStorage(INDEXES_STORAGE_NAME);
    this.nodeIndexesSource = nodeIndexesSource;

    DataSource<Bytes, NodeRecordInfo> nodeTable =
        new CodecSource<>(
            nodeTableSource,
            key -> key,
            serializerFactory.getSerializer(NodeRecordInfo.class),
            serializerFactory.getDeserializer(NodeRecordInfo.class));
    HoleyList<NodeIndex> nodeIndexesTable =
        new DataSourceList<>(
            nodeIndexesSource,
            serializerFactory.getSerializer(NodeIndex.class),
            serializerFactory.getDeserializer(NodeIndex.class));
    this.homeNodeSource = SingleValueSource.fromDataSource(nodeTable, HOME_NODE_KEY);
    this.nodeTable = new NodeTableImpl(nodeTable, nodeIndexesTable, homeNodeSource);
  }

  @Override
  public NodeTable get() {
    return nodeTable;
  }

  @Override
  public SingleValueSource<NodeRecordInfo> getHomeNodeSource() {
    return homeNodeSource;
  }

  @Override
  public void commit() {
    nodeTableSource.flush();
    nodeIndexesSource.flush();
  }
}
