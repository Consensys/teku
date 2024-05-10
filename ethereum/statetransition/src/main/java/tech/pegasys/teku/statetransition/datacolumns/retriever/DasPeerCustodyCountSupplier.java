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

import static java.lang.Integer.max;
import static java.lang.Integer.min;

import org.apache.tuweni.units.bigints.UInt256;

public interface DasPeerCustodyCountSupplier {

  static DasPeerCustodyCountSupplier createStub(int defaultValue) {
    return (__) -> defaultValue;
  }

  static DasPeerCustodyCountSupplier capped(
      DasPeerCustodyCountSupplier delegate, int minValue, int maxValue) {
    return (nodeId) -> min(maxValue, max(minValue, delegate.getCustodyCountForPeer(nodeId)));
  }

  int getCustodyCountForPeer(UInt256 nodeId);
}
