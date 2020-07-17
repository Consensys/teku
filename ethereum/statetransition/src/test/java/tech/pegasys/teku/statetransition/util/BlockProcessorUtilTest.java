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

package tech.pegasys.teku.statetransition.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.primitives.UnsignedLong;
import java.util.Arrays;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.junit.BouncyCastleExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.core.BlockProcessorUtil;
import tech.pegasys.teku.core.exceptions.BlockProcessingException;
import tech.pegasys.teku.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.datastructures.operations.DepositData;
import tech.pegasys.teku.datastructures.operations.DepositMessage;
import tech.pegasys.teku.datastructures.operations.DepositWithIndex;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Fork;
import tech.pegasys.teku.datastructures.state.Validator;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.datastructures.util.MerkleTree;
import tech.pegasys.teku.datastructures.util.OptimizedMerkleTree;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;
import tech.pegasys.teku.ssz.SSZTypes.SSZMutableList;
import tech.pegasys.teku.util.config.Constants;

@ExtendWith(BouncyCastleExtension.class)
class BlockProcessorUtilTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();

  @Test
  void processDepositAddsNewValidatorWhenPubkeyIsNotFoundInRegistry()
      throws BlockProcessingException {
    // Create a deposit
    DepositData depositInput = dataStructureUtil.randomDepositData();
    BLSPublicKey pubkey = depositInput.getPubkey();
    Bytes32 withdrawalCredentials = depositInput.getWithdrawal_credentials();
    UnsignedLong amount = depositInput.getAmount();

    // Add the deposit to a Merkle tree so that we can get the root to put into the state
    MerkleTree depositMerkleTree = new OptimizedMerkleTree(Constants.DEPOSIT_CONTRACT_TREE_DEPTH);
    depositMerkleTree.add(depositInput.hash_tree_root());

    // Create the state and insert the Merkle root of the deposit data
    BeaconState beaconState =
        createBeaconState()
            .updated(
                state ->
                    state.setEth1_data(
                        new Eth1Data(
                            depositMerkleTree.getRoot(), UnsignedLong.valueOf(1), Bytes32.ZERO)));

    SSZMutableList<DepositWithIndex> deposits =
        SSZList.createMutable(DepositWithIndex.class, Constants.MAX_DEPOSITS);
    deposits.add(
        new DepositWithIndex(depositMerkleTree.getProof(0), depositInput, UnsignedLong.valueOf(0)));

    int originalValidatorRegistrySize = beaconState.getValidators().size();
    int originalValidatorBalancesSize = beaconState.getBalances().size();

    // Attempt to process deposit with above data.
    beaconState =
        beaconState.updated(state -> BlockProcessorUtil.process_deposits(state, deposits));

    assertTrue(
        beaconState.getValidators().size() == (originalValidatorRegistrySize + 1),
        "No validator was added to the validator registry.");
    assertTrue(
        beaconState.getBalances().size() == (originalValidatorBalancesSize + 1),
        "No balance was added to the validator balances.");
    assertEquals(
        Validator.create(
            pubkey,
            withdrawalCredentials,
            UnsignedLong.valueOf(Constants.MAX_EFFECTIVE_BALANCE),
            false,
            Constants.FAR_FUTURE_EPOCH,
            Constants.FAR_FUTURE_EPOCH,
            Constants.FAR_FUTURE_EPOCH,
            Constants.FAR_FUTURE_EPOCH),
        beaconState.getValidators().get(originalValidatorRegistrySize));
    assertEquals(amount, beaconState.getBalances().get(originalValidatorBalancesSize));
  }

  @Test
  void processDepositTopsUpValidatorBalanceWhenPubkeyIsFoundInRegistry()
      throws BlockProcessingException {
    // Create a deposit
    DepositData depositInput = dataStructureUtil.randomDepositData();
    BLSPublicKey pubkey = depositInput.getPubkey();
    Bytes32 withdrawalCredentials = depositInput.getWithdrawal_credentials();
    UnsignedLong amount = depositInput.getAmount();

    // Add the deposit to a Merkle tree so that we can get the root to put into the state
    MerkleTree depositMerkleTree = new OptimizedMerkleTree(Constants.DEPOSIT_CONTRACT_TREE_DEPTH);
    depositMerkleTree.add(depositInput.hash_tree_root());

    Validator knownValidator =
        Validator.create(
            pubkey,
            withdrawalCredentials,
            UnsignedLong.valueOf(Constants.MAX_EFFECTIVE_BALANCE),
            false,
            Constants.FAR_FUTURE_EPOCH,
            Constants.FAR_FUTURE_EPOCH,
            Constants.FAR_FUTURE_EPOCH,
            Constants.FAR_FUTURE_EPOCH);

    BeaconState beaconState =
        createBeaconState(amount, knownValidator)
            .updated(
                state ->
                    state.setEth1_data(
                        new Eth1Data(
                            depositMerkleTree.getRoot(), UnsignedLong.valueOf(1), Bytes32.ZERO)));

    int originalValidatorRegistrySize = beaconState.getValidators().size();
    int originalValidatorBalancesSize = beaconState.getBalances().size();

    SSZMutableList<DepositWithIndex> deposits =
        SSZList.createMutable(DepositWithIndex.class, Constants.MAX_DEPOSITS);
    deposits.add(
        new DepositWithIndex(depositMerkleTree.getProof(0), depositInput, UnsignedLong.valueOf(0)));

    // Attempt to process deposit with above data.
    beaconState =
        beaconState.updated(state -> BlockProcessorUtil.process_deposits(state, deposits));

    assertTrue(
        beaconState.getValidators().size() == originalValidatorRegistrySize,
        "A new validator was added to the validator registry, but should not have been.");
    assertTrue(
        beaconState.getBalances().size() == originalValidatorBalancesSize,
        "A new balance was added to the validator balances, but should not have been.");
    assertEquals(
        knownValidator, beaconState.getValidators().get(originalValidatorRegistrySize - 1));
    assertEquals(
        amount.times(UnsignedLong.valueOf(2L)),
        beaconState.getBalances().get(originalValidatorBalancesSize - 1));
  }

  @Test
  void processDepositHandlesDepositWithInvalidPublicKey() throws BlockProcessingException {
    // The following deposit uses a "rogue" public key that is not in the G1 group
    BLSPublicKey pubkey =
        BLSPublicKey.fromBytesCompressed(
            Bytes.fromHexString(
                "0x9378a6e3984e96d2cd50450c76ca14732f1300efa04aecdb805b22e6d6926a85ef409e8f3acf494a1481090bf32ce3bd"));
    Bytes32 withdrawalCredentials =
        Bytes32.fromHexString("0x79e43d39ee55749c55994a7ab2a3cb91460cec544fdbf27eb5717c43f970c1b6");
    UnsignedLong amount = UnsignedLong.valueOf(1000000000L);
    BLSSignature signature =
        BLSSignature.fromBytes(
            Bytes.fromHexString(
                "0xddc1ca509e29c6452441069f26da6e073589b3bd1cace50e3427426af5bfdd566d077d4bdf618e249061b9770471e3d515779aa758b8ccb4b06226a8d5ebc99e19d4c3278e5006b837985bec4e0ce39df92c1f88d1afd0f98dbae360024a390d"));
    DepositData depositInput =
        new DepositData(new DepositMessage(pubkey, withdrawalCredentials, amount), signature);

    // Add the deposit to a Merkle tree so that we can get the root to put into the state
    MerkleTree depositMerkleTree = new OptimizedMerkleTree(Constants.DEPOSIT_CONTRACT_TREE_DEPTH);
    depositMerkleTree.add(depositInput.hash_tree_root());

    // Create the state and insert the Merkle root of the deposit data
    BeaconState beaconState =
        createBeaconState()
            .updated(
                state ->
                    state.setEth1_data(
                        new Eth1Data(
                            depositMerkleTree.getRoot(), UnsignedLong.valueOf(1), Bytes32.ZERO)));

    SSZMutableList<DepositWithIndex> deposits =
        SSZList.createMutable(DepositWithIndex.class, Constants.MAX_DEPOSITS);
    deposits.add(
        new DepositWithIndex(depositMerkleTree.getProof(0), depositInput, UnsignedLong.valueOf(0)));

    int originalValidatorRegistrySize = beaconState.getValidators().size();
    int originalValidatorBalancesSize = beaconState.getBalances().size();

    // Attempt to process deposit with above data. We expect to fail, but not throw an exception.
    beaconState =
        beaconState.updated(state -> BlockProcessorUtil.process_deposits(state, deposits));

    assertTrue(
        beaconState.getValidators().size() == originalValidatorRegistrySize,
        "The validator was added to the validator registry.");
    assertTrue(
        beaconState.getBalances().size() == originalValidatorBalancesSize,
        "The balance was added to the validator balances.");
  }

  private BeaconState createBeaconState() {
    return createBeaconState(false, null, null);
  }

  private BeaconState createBeaconState(UnsignedLong amount, Validator knownValidator) {
    return createBeaconState(true, amount, knownValidator);
  }

  private BeaconState createBeaconState(
      boolean addToList, UnsignedLong amount, Validator knownValidator) {
    return BeaconState.createEmpty()
        .updated(
            beaconState -> {
              beaconState.setSlot(dataStructureUtil.randomUnsignedLong());
              beaconState.setFork(
                  new Fork(
                      Constants.GENESIS_FORK_VERSION,
                      Constants.GENESIS_FORK_VERSION,
                      UnsignedLong.valueOf(Constants.GENESIS_EPOCH)));

              SSZMutableList<Validator> validatorList =
                  SSZList.createMutable(
                      Arrays.asList(
                          dataStructureUtil.randomValidator(),
                          dataStructureUtil.randomValidator(),
                          dataStructureUtil.randomValidator()),
                      Constants.VALIDATOR_REGISTRY_LIMIT,
                      Validator.class);
              SSZMutableList<UnsignedLong> balanceList =
                  SSZList.createMutable(
                      Arrays.asList(
                          dataStructureUtil.randomUnsignedLong(),
                          dataStructureUtil.randomUnsignedLong(),
                          dataStructureUtil.randomUnsignedLong()),
                      Constants.VALIDATOR_REGISTRY_LIMIT,
                      UnsignedLong.class);

              if (addToList) {
                validatorList.add(knownValidator);
                balanceList.add(amount);
              }

              beaconState.getValidators().addAll(validatorList);
              beaconState.getBalances().addAll(balanceList);
            });
  }
}
