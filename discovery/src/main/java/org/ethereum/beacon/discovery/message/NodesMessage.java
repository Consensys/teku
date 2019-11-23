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

package org.ethereum.beacon.discovery.message;

import com.google.common.base.Objects;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.web3j.rlp.RlpEncoder;
import org.web3j.rlp.RlpList;
import org.web3j.rlp.RlpString;
import org.web3j.rlp.RlpType;

/**
 * NODES is the response to a FINDNODE or TOPICQUERY message. Multiple NODES messages may be sent as
 * responses to a single query.
 */
public class NodesMessage implements V5Message {
  // Unique request id
  private final Bytes requestId;
  // Total number of responses to the request
  private final Integer total;
  // List of nodes upon request
  private final Supplier<List<NodeRecord>> nodeRecordsSupplier;
  // Size of nodes in current response
  private final Integer nodeRecordsSize;
  private List<NodeRecord> nodeRecords = null;

  public NodesMessage(
      Bytes requestId,
      Integer total,
      Supplier<List<NodeRecord>> nodeRecordsSupplier,
      Integer nodeRecordsSize) {
    this.requestId = requestId;
    this.total = total;
    this.nodeRecordsSupplier = nodeRecordsSupplier;
    this.nodeRecordsSize = nodeRecordsSize;
  }

  public static NodesMessage fromRlp(List<RlpType> rlpList, NodeRecordFactory nodeRecordFactory) {
    RlpList nodeRecords = (RlpList) rlpList.get(2);
    return new NodesMessage(
        Bytes.wrap(((RlpString) rlpList.get(0)).getBytes()),
        ((RlpString) rlpList.get(1)).asPositiveBigInteger().intValueExact(),
        () ->
            nodeRecords.getValues().stream()
                .map(rl -> nodeRecordFactory.fromRlpList((RlpList) rl))
                .collect(Collectors.toList()),
        nodeRecords.getValues().size());
  }

  @Override
  public Bytes getRequestId() {
    return requestId;
  }

  public Integer getTotal() {
    return total;
  }

  public synchronized List<NodeRecord> getNodeRecords() {
    if (nodeRecords == null) {
      this.nodeRecords = nodeRecordsSupplier.get();
    }
    return nodeRecords;
  }

  public Integer getNodeRecordsSize() {
    return nodeRecordsSize;
  }

  @Override
  public Bytes getBytes() {
    return Bytes.concatenate(
        Bytes.of(MessageCode.NODES.byteCode()),
        Bytes.wrap(
            RlpEncoder.encode(
                new RlpList(
                    RlpString.create(requestId.toArray()),
                    RlpString.create(total),
                    new RlpList(
                        getNodeRecords().stream()
                            .map(NodeRecord::asRlp)
                            .collect(Collectors.toList()))))));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    NodesMessage that = (NodesMessage) o;
    return Objects.equal(requestId, that.requestId)
        && Objects.equal(total, that.total)
        && Objects.equal(nodeRecordsSize, that.nodeRecordsSize);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(requestId, total, nodeRecordsSize);
  }

  @Override
  public String toString() {
    return "NodesMessage{"
        + "requestId="
        + requestId
        + ", total="
        + total
        + ", nodeRecordsSize="
        + nodeRecordsSize
        + '}';
  }
}
