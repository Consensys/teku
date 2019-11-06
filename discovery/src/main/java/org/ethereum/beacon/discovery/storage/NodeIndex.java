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
import org.apache.tuweni.bytes.Bytes;
import org.web3j.rlp.RlpDecoder;
import org.web3j.rlp.RlpEncoder;
import org.web3j.rlp.RlpList;
import org.web3j.rlp.RlpString;
import org.web3j.rlp.RlpType;

/** Node Index. Stores several node keys. */
public class NodeIndex {
  private List<Bytes> entries;

  public NodeIndex() {
    this.entries = new ArrayList<>();
  }

  public static NodeIndex fromRlpBytes(Bytes bytes) {
    RlpList internalList = (RlpList) RlpDecoder.decode(bytes.toArray()).getValues().get(0);
    List<Bytes> entries = new ArrayList<>();
    for (RlpType entry : internalList.getValues()) {
      entries.add(Bytes.wrap(((RlpString) entry).getBytes()));
    }
    NodeIndex res = new NodeIndex();
    res.setEntries(entries);
    return res;
  }

  public List<Bytes> getEntries() {
    return entries;
  }

  public void setEntries(List<Bytes> entries) {
    this.entries = entries;
  }

  public Bytes toRlpBytes() {
    List<RlpType> values = new ArrayList<>();
    for (Bytes Bytes : getEntries()) {
      values.add(RlpString.create(Bytes.toArray()));
    }
    byte[] bytes = RlpEncoder.encode(new RlpList(values));
    return Bytes.wrap(bytes);
  }
}
