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
import java.util.ArrayList;
import java.util.List;
import tech.pegasys.teku.datastructures.blocks.Eth1BlockData;
import tech.pegasys.teku.datastructures.operations.DepositWithIndex;
import tech.pegasys.teku.storage.server.Database;

public class TrackingEth1DepositChannel implements Eth1DepositChannel {
  private final Database database;
  private final List<DepositWithIndex> deposits = new ArrayList<>();
  private final List<Eth1BlockData> blockDataList = new ArrayList<>();
  private final List<UnsignedLong> finalizedDeposits = new ArrayList<>();

  public TrackingEth1DepositChannel(final Database database) {
    this.database = database;
  }

  public List<DepositWithIndex> getDeposits() {
    return deposits;
  }

  public List<Eth1BlockData> getBlockDataList() {
    return blockDataList;
  }

  public List<UnsignedLong> getFinalizedDeposits() {
    return finalizedDeposits;
  }

  @Override
  public void addEth1Deposit(final DepositWithIndex depositWithIndex) {
    deposits.add(depositWithIndex);
    database.addEth1Deposit(depositWithIndex);
  }

  @Override
  public void addEth1BlockData(final UnsignedLong timestamp, final Eth1BlockData eth1BlockData) {
    this.blockDataList.add(eth1BlockData);
    database.addEth1BlockData(timestamp, eth1BlockData);
  }

  @Override
  public void eth1DepositsFinalized(final UnsignedLong depositIndex) {
    this.finalizedDeposits.add(depositIndex);
    database.pruneEth1Deposits(depositIndex);
  }
}
