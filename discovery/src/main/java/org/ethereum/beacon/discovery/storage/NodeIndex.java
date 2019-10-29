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

import java.util.ArrayList;
import java.util.List;
import org.web3j.rlp.RlpDecoder;
import org.web3j.rlp.RlpEncoder;
import org.web3j.rlp.RlpList;
import org.web3j.rlp.RlpString;
import org.web3j.rlp.RlpType;
import tech.pegasys.artemis.ethereum.core.Hash32;
import tech.pegasys.artemis.util.bytes.Bytes32;
import tech.pegasys.artemis.util.bytes.BytesValue;

/** Node Index. Stores several node keys. */
public class NodeIndex {
  private List<Hash32> entries;

  public NodeIndex() {
    this.entries = new ArrayList<>();
  }

  public static NodeIndex fromRlpBytes(BytesValue bytes) {
    RlpList internalList = (RlpList) RlpDecoder.decode(bytes.extractArray()).getValues().get(0);
    List<Hash32> entries = new ArrayList<>();
    for (RlpType entry : internalList.getValues()) {
      entries.add(Hash32.wrap(Bytes32.wrap(((RlpString) entry).getBytes())));
    }
    NodeIndex res = new NodeIndex();
    res.setEntries(entries);
    return res;
  }

  public List<Hash32> getEntries() {
    return entries;
  }

  public void setEntries(List<Hash32> entries) {
    this.entries = entries;
  }

  public BytesValue toRlpBytes() {
    List<RlpType> values = new ArrayList<>();
    for (Hash32 hash32 : getEntries()) {
      values.add(RlpString.create(hash32.extractArray()));
    }
    byte[] bytes = RlpEncoder.encode(new RlpList(values));
    return BytesValue.wrap(bytes);
  }
}
