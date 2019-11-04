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

package org.ethereum.beacon.discovery.schema;

import com.google.common.base.Objects;
import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.type.BytesValue;
import org.web3j.rlp.RlpDecoder;
import org.web3j.rlp.RlpEncoder;
import org.web3j.rlp.RlpList;
import org.web3j.rlp.RlpString;
import org.web3j.rlp.RlpType;

// import tech.pegasys.artemis.util.bytes.Bytes;

/**
 * Container for {@link NodeRecord}. Also saves all necessary data about presence of this node and
 * last test of its availability
 */
public class NodeRecordInfo {
  private final NodeRecord node;
  private final Long lastRetry;
  private final NodeStatus status;
  private final Integer retry;

  public NodeRecordInfo(NodeRecord node, Long lastRetry, NodeStatus status, Integer retry) {
    this.node = node;
    this.lastRetry = lastRetry;
    this.status = status;
    this.retry = retry;
  }

  public static NodeRecordInfo createDefault(NodeRecord nodeRecord) {
    return new NodeRecordInfo(nodeRecord, -1L, NodeStatus.ACTIVE, 0);
  }

  public static NodeRecordInfo fromRlpBytes(BytesValue bytes, NodeRecordFactory nodeRecordFactory) {
    RlpList internalList = (RlpList) RlpDecoder.decode(bytes.extractArray()).getValues().get(0);
    return new NodeRecordInfo(
        nodeRecordFactory.fromBytes(((RlpString) internalList.getValues().get(0)).getBytes()),
        ((RlpString) internalList.getValues().get(1)).asPositiveBigInteger().longValue(),
        NodeStatus.fromNumber(((RlpString) internalList.getValues().get(2)).getBytes()[0]),
        ((RlpString) internalList.getValues().get(1)).asPositiveBigInteger().intValue());
  }

  public static NodeRecordInfo fromRlpBytes(Bytes bytes, NodeRecordFactory nodeRecordFactory) {
    return fromRlpBytes(BytesValue.wrap(bytes.toArray()), nodeRecordFactory);
  }

  public BytesValue toRlpBytes() {
    List<RlpType> values = new ArrayList<>();
    values.add(RlpString.create(getNode().serialize().toArray()));
    values.add(RlpString.create(getLastRetry()));
    values.add(RlpString.create(getStatus().byteCode()));
    values.add(RlpString.create(getRetry()));
    byte[] bytes = RlpEncoder.encode(new RlpList(values));
    return BytesValue.wrap(bytes);
  }

  public NodeRecord getNode() {
    return (NodeRecord) node;
  }

  public Long getLastRetry() {
    return lastRetry;
  }

  public NodeStatus getStatus() {
    return status;
  }

  public Integer getRetry() {
    return retry;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    NodeRecordInfo that = (NodeRecordInfo) o;
    return Objects.equal(node, that.node)
        && Objects.equal(lastRetry, that.lastRetry)
        && status == that.status
        && Objects.equal(retry, that.retry);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(node, lastRetry, status, retry);
  }

  @Override
  public String toString() {
    return "NodeRecordInfo{"
        + "node="
        + node
        + ", lastRetry="
        + lastRetry
        + ", status="
        + status
        + ", retry="
        + retry
        + '}';
  }
}
