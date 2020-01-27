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

package tech.pegasys.artemis.datastructures.util;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.artemis.util.config.Constants.DEPOSIT_CONTRACT_TREE_DEPTH;
import static tech.pegasys.artemis.util.config.Constants.MIN_DEPOSIT_AMOUNT;

import com.google.common.primitives.UnsignedLong;
import com.google.gson.JsonElement;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.Hash;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.operations.DepositData;
import tech.pegasys.artemis.datastructures.operations.DepositWithIndex;
import tech.pegasys.artemis.pow.contract.DepositContract;
import tech.pegasys.artemis.util.SSZTypes.SSZVector;

public class DepositUtil {

  public static void calcDepositProofs(List<? extends Deposit> deposits) {
    MerkleTree<Bytes32> merkleTree = new MerkleTree<Bytes32>(DEPOSIT_CONTRACT_TREE_DEPTH);
    deposits.stream()
        .forEach(
            deposit -> {
              Bytes32 value = deposit.getData().hash_tree_root();
              merkleTree.add(value);
              deposit.setProof(merkleTree.getProofTreeByValue(value));
            });
  }

  public static List<DepositWithIndex> applyBranchProofs(
      MerkleTree<DepositWithIndex> merkleTree, List<DepositWithIndex> deposits) {
    for (int i = 0; i < deposits.size(); i++)
      deposits.get(i).setProof(new SSZVector<>(merkleTree.getProofTreeByIndex(i), Bytes32.class));
    return deposits;
  }

  public static boolean validateDeposits(
      List<DepositWithIndex> deposits, Bytes32 root, int height) {
    for (int i = 0; i < deposits.size(); i++) {
      if (BeaconStateUtil.is_valid_merkle_branch(
          Hash.sha2_256(deposits.get(i).getData().serialize()),
          deposits.get(i).getProof(),
          height,
          i,
          root)) ;
      else return false;
    }
    return true;
  }

  public static List<Bytes32> insertBranch(
      int branchIndex, Bytes32 branchValue, List<Bytes32> branch, int height) {
    Bytes32 value = branchValue;
    for (int j = 0; j < height; j++) {
      if (j < branchIndex) {
        value = Hash.sha2_256(Bytes.concatenate(branch.get(j), value));
      } else break;
    }
    branch.set(branchIndex, value);
    return branch;
  }

  public static Bytes32 calculateRoot(
      List<Bytes32> branch, List<Bytes32> zerohashes, int deposit_count, int height) {
    Bytes32 root = Bytes32.ZERO;
    int size = deposit_count;
    for (int h = 0; h < height; h++) {
      if (size % 2 != 0) {
        root = Hash.sha2_256(Bytes.concatenate(branch.get(h), root));
      } else {
        root = Hash.sha2_256(Bytes.concatenate(root, zerohashes.get(h)));
      }
      size /= 2;
    }
    return root;
  }

  public static int calculateBranchIndex(int index, int height) {
    int branchIndex = 0;
    int power_of_two = 2;
    for (int x = 0; x < height; x++) {
      if ((index + 1) % power_of_two != 0) break;
      branchIndex++;
      power_of_two *= 2;
    }
    return branchIndex;
  }

  public static Bytes32 calculatePubKeyRoot(DepositWithIndex deposit) {
    return Hash.sha2_256(
        Bytes.concatenate(
            deposit.getData().getPubkey().getPublicKey().toBytesCompressed(),
            Bytes.wrap(new byte[16])));
  }

  public static Bytes32 calculateSignatureRoot(DepositWithIndex deposit) {
    Bytes32 signature_root_start =
        Hash.sha2_256(
            Bytes.wrap(
                Arrays.copyOfRange(
                    deposit.getData().getSignature().getSignature().toBytesCompressed().toArray(),
                    0,
                    64)));
    Bytes signature_root_end =
        Hash.sha2_256(
            Bytes.concatenate(
                Bytes.wrap(
                    Arrays.copyOfRange(
                        deposit
                            .getData()
                            .getSignature()
                            .getSignature()
                            .toBytesCompressed()
                            .toArray(),
                        64,
                        96)),
                Bytes32.ZERO));
    return Hash.sha2_256(Bytes.concatenate(signature_root_start, signature_root_end));
  }

  public static Bytes32 calculateBranchValue(
      Bytes32 pubkey_root, Bytes32 signature_root, DepositWithIndex deposit) {
    Bytes32 value_start =
        Hash.sha2_256(
            Bytes.concatenate(pubkey_root, deposit.getData().getWithdrawal_credentials()));
    Bytes32 value_end =
        Hash.sha2_256(
            Bytes.concatenate(
                Bytes.ofUnsignedLong(deposit.getData().getAmount().longValue()),
                Bytes.wrap(new byte[24]),
                signature_root));

    return Hash.sha2_256(Bytes.concatenate(value_start, value_end));
  }

  public static List<DepositWithIndex> sortDepositsByIndexAscending(
      List<DepositWithIndex> deposits) {
    deposits.sort(
        new Comparator<DepositWithIndex>() {
          @Override
          public int compare(DepositWithIndex o1, DepositWithIndex o2) {
            return o1.getIndex().compareTo(o2.getIndex());
          }
        });
    return deposits;
  }

  public static DepositWithIndex convertDepositEventToOperationDeposit(
      tech.pegasys.artemis.pow.event.Deposit event) {
    checkArgument(
        event.getAmount().compareTo(UnsignedLong.valueOf(MIN_DEPOSIT_AMOUNT)) >= 0,
        "Deposit amount too low");
    DepositData data =
        new DepositData(
            event.getPubkey(),
            event.getWithdrawal_credentials(),
            event.getAmount(),
            event.getSignature());
    return new DepositWithIndex(data, event.getMerkle_tree_index(), event.getResponse().log);
  }

  // deprecated, being used until a new validators_test_data.json can be generated
  public static tech.pegasys.artemis.pow.event.Deposit convertJsonDataToEventDeposit(
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
    return new tech.pegasys.artemis.pow.event.Deposit(response);
  }

  public static UnsignedLong getEpochBlockTimeByDepositBlockHash(Bytes32 blockHash, String provider)
      throws IOException {
    Web3j web3 = Web3j.build(new HttpService(provider));
    return UnsignedLong.valueOf(
        web3.ethGetBlockByHash(blockHash.toHexString(), false).send().getBlock().getTimestamp());
  }
}
