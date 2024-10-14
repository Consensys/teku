/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.statetransition.datacolumns.retriever;

import java.util.HashMap;
import java.util.Map;
import org.apache.tuweni.units.bigints.UInt256;

public class DasPeerCustodyCountSupplierStub implements DasPeerCustodyCountSupplier {
  private final int defaultCount;
  private final Map<UInt256, Integer> customCounts = new HashMap<>();

  public DasPeerCustodyCountSupplierStub(int defaultCount) {
    this.defaultCount = defaultCount;
  }

  @Override
  public int getCustodyCountForPeer(UInt256 nodeId) {
    return customCounts.getOrDefault(nodeId, defaultCount);
  }

  public void setCustomCount(UInt256 nodeId, int count) {
    customCounts.put(nodeId, count);
  }
}
