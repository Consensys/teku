/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.storage.api;

import com.google.common.primitives.UnsignedLong;
import tech.pegasys.teku.datastructures.blocks.Eth1BlockData;
import tech.pegasys.teku.datastructures.operations.DepositWithIndex;

public interface Eth1DepositChannel {
  /**
   * Called each time an eth1 deposit is added
   *
   * @param depositWithIndex the deposit to add to cache
   */
  void addEth1Deposit(final DepositWithIndex depositWithIndex);

  void addEth1BlockData(final UnsignedLong timestamp, final Eth1BlockData eth1BlockData);

  /**
   * Called each time eth1 deposits are added to a block to clear the cached deposits.
   *
   * @param depositIndex The highest index to clear from the cached deposits
   */
  void eth1DepositsFinalized(UnsignedLong depositIndex);
}
