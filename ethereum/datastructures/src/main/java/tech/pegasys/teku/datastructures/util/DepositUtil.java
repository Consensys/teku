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

package tech.pegasys.teku.datastructures.util;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.util.config.Constants.MIN_DEPOSIT_AMOUNT;

import com.google.gson.JsonElement;
import java.util.Arrays;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.datastructures.operations.DepositData;
import tech.pegasys.teku.datastructures.operations.DepositWithIndex;
import tech.pegasys.teku.pow.contract.DepositContract;

public class DepositUtil {

  public static DepositWithIndex convertDepositEventToOperationDeposit(
      tech.pegasys.teku.pow.event.Deposit event) {
    checkArgument(
        event.getAmount().isGreaterThanOrEqualTo(MIN_DEPOSIT_AMOUNT), "Deposit amount too low");
    DepositData data =
        new DepositData(
            event.getPubkey(),
            event.getWithdrawal_credentials(),
            event.getAmount(),
            event.getSignature());
    return new DepositWithIndex(data, event.getMerkle_tree_index());
  }

  // deprecated, being used until a new validators_test_data.json can be generated
  public static tech.pegasys.teku.pow.event.Deposit convertJsonDataToEventDeposit(
      JsonElement event) {
    byte[] data = Bytes.fromHexString(event.getAsJsonObject().get("data").getAsString()).toArray();
    byte[] index =
        Bytes.fromHexString(event.getAsJsonObject().get("merkle_tree_index").getAsString())
            .toArray();
    DepositContract.DepositEventEventResponse response =
        new DepositContract.DepositEventEventResponse();
    response.pubkey = Arrays.copyOfRange(data, 0, 48);
    response.withdrawal_credentials = Arrays.copyOfRange(data, 48, 80);
    response.amount = Arrays.copyOfRange(data, 80, 88);
    response.signature = Arrays.copyOfRange(data, 88, 184);
    response.index = index;
    return new tech.pegasys.teku.pow.event.Deposit(response);
  }
}
