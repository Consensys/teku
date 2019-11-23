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
import org.apache.tuweni.bytes.Bytes;
import org.web3j.rlp.RlpEncoder;
import org.web3j.rlp.RlpList;
import org.web3j.rlp.RlpString;
import org.web3j.rlp.RlpType;

/**
 * FINDNODE queries for nodes at the given logarithmic distance from the recipient's node ID. The
 * node IDs of all nodes in the response must have a shared prefix length of distance with the
 * recipient's node ID. A request with distance 0 should return the recipient's current record as
 * the only result.
 */
public class FindNodeMessage implements V5Message {
  // Unique request id
  private final Bytes requestId;
  // The requested log2 distance, a positive integer
  private final Integer distance;

  public FindNodeMessage(Bytes requestId, Integer distance) {
    this.requestId = requestId;
    this.distance = distance;
  }

  public static FindNodeMessage fromRlp(List<RlpType> rlpList) {
    return new FindNodeMessage(
        Bytes.wrap(((RlpString) rlpList.get(0)).getBytes()),
        ((RlpString) rlpList.get(1)).asPositiveBigInteger().intValueExact());
  }

  @Override
  public Bytes getRequestId() {
    return requestId;
  }

  public Integer getDistance() {
    return distance;
  }

  @Override
  public Bytes getBytes() {
    return Bytes.concatenate(
        Bytes.of(MessageCode.FINDNODE.byteCode()),
        Bytes.wrap(
            RlpEncoder.encode(
                new RlpList(RlpString.create(requestId.toArray()), RlpString.create(distance)))));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    FindNodeMessage that = (FindNodeMessage) o;
    return Objects.equal(requestId, that.requestId) && Objects.equal(distance, that.distance);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(requestId, distance);
  }

  @Override
  public String toString() {
    return "FindNodeMessage{" + "requestId=" + requestId + ", distance=" + distance + '}';
  }
}
