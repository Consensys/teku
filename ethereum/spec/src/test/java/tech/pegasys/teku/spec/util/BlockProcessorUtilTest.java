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

package tech.pegasys.teku.spec.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import org.apache.tuweni.junit.BouncyCastleExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecFactory;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.constants.SpecConstants;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.operations.DepositData;
import tech.pegasys.teku.spec.datastructures.operations.DepositMessage;
import tech.pegasys.teku.spec.datastructures.operations.DepositWithIndex;
import tech.pegasys.teku.spec.datastructures.state.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.util.MerkleTree;
import tech.pegasys.teku.spec.datastructures.util.OptimizedMerkleTree;
import tech.pegasys.teku.spec.statetransition.exceptions.BlockProcessingException;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;
import tech.pegasys.teku.ssz.SSZTypes.SSZMutableList;

@ExtendWith(BouncyCastleExtension.class)
class BlockProcessorUtilTest {
  private final Spec spec = SpecFactory.createMinimal();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private final SpecVersion genesisSpec = spec.getGenesisSpec();
  private final SpecConstants specConstants = genesisSpec.getConstants();
  private final BlockProcessorUtil blockProcessorUtil = genesisSpec.getBlockProcessorUtil();

  @Test
  void processDepositAddsNewValidatorWhenPubkeyIsNotFoundInRegistry()
      throws BlockProcessingException {
    // Create a deposit
    DepositData depositInput = dataStructureUtil.randomDepositData();
    BLSPublicKey pubkey = depositInput.getPubkey();
    Bytes32 withdrawalCredentials = depositInput.getWithdrawal_credentials();
    UInt64 amount = depositInput.getAmount();

    BeaconState preState = createBeaconState();

    int originalValidatorRegistrySize = preState.getValidators().size();
    int originalValidatorBalancesSize = preState.getBalances().size();

    BeaconState postState = processDepositHelper(preState, depositInput);

    assertEquals(
        postState.getValidators().size(),
        originalValidatorRegistrySize + 1,
        "No validator was added to the validator registry.");
    assertEquals(
        postState.getBalances().size(),
        originalValidatorBalancesSize + 1,
        "No balance was added to the validator balances.");
    assertEquals(
        makeValidator(pubkey, withdrawalCredentials),
        postState.getValidators().get(originalValidatorRegistrySize));
    assertEquals(amount, postState.getBalances().get(originalValidatorBalancesSize));
  }

  @Test
  void processDepositTopsUpValidatorBalanceWhenPubkeyIsFoundInRegistry()
      throws BlockProcessingException {
    // Create a deposit
    DepositData depositInput = dataStructureUtil.randomDepositData();
    BLSPublicKey pubkey = depositInput.getPubkey();
    Bytes32 withdrawalCredentials = depositInput.getWithdrawal_credentials();
    UInt64 amount = depositInput.getAmount();

    Validator knownValidator = makeValidator(pubkey, withdrawalCredentials);

    BeaconState preState = createBeaconState(amount, knownValidator);

    int originalValidatorRegistrySize = preState.getValidators().size();
    int originalValidatorBalancesSize = preState.getBalances().size();

    BeaconState postState = processDepositHelper(preState, depositInput);

    assertEquals(
        postState.getValidators().size(),
        originalValidatorRegistrySize,
        "A new validator was added to the validator registry, but should not have been.");
    assertEquals(
        postState.getBalances().size(),
        originalValidatorBalancesSize,
        "A new balance was added to the validator balances, but should not have been.");
    assertEquals(knownValidator, postState.getValidators().get(originalValidatorRegistrySize - 1));
    assertEquals(amount.times(2L), postState.getBalances().get(originalValidatorBalancesSize - 1));
  }

  @Test
  void processDepositIgnoresDepositWithInvalidPublicKey() throws BlockProcessingException {
    // The following deposit uses a "rogue" public key that is not in the G1 group
    BLSPublicKey pubkey =
        BLSPublicKey.fromBytesCompressed(
            Bytes48.fromHexString(
                "0x9378a6e3984e96d2cd50450c76ca14732f1300efa04aecdb805b22e6d6926a85ef409e8f3acf494a1481090bf32ce3bd"));
    Bytes32 withdrawalCredentials =
        Bytes32.fromHexString("0x79e43d39ee55749c55994a7ab2a3cb91460cec544fdbf27eb5717c43f970c1b6");
    UInt64 amount = UInt64.valueOf(1000000000L);
    BLSSignature signature =
        BLSSignature.fromBytesCompressed(
            Bytes.fromHexString(
                "0xddc1ca509e29c6452441069f26da6e073589b3bd1cace50e3427426af5bfdd566d077d4bdf618e249061b9770471e3d515779aa758b8ccb4b06226a8d5ebc99e19d4c3278e5006b837985bec4e0ce39df92c1f88d1afd0f98dbae360024a390d"));
    DepositData depositInput =
        new DepositData(new DepositMessage(pubkey, withdrawalCredentials, amount), signature);

    BeaconState preState = createBeaconState();

    int originalValidatorRegistrySize = preState.getValidators().size();
    int originalValidatorBalancesSize = preState.getBalances().size();

    BeaconState postState = processDepositHelper(preState, depositInput);

    assertEquals(
        postState.getValidators().size(),
        originalValidatorRegistrySize,
        "The validator was added to the validator registry.");
    assertEquals(
        postState.getBalances().size(),
        originalValidatorBalancesSize,
        "The balance was added to the validator balances.");
    assertEquals(
        preState.getBalances().hash_tree_root(),
        postState.getBalances().hash_tree_root(),
        "The balances list has changed.");
  }

  private BeaconState createBeaconState() {
    return createBeaconState(false, null, null);
  }

  private BeaconState createBeaconState(UInt64 amount, Validator knownValidator) {
    return createBeaconState(true, amount, knownValidator);
  }

  private BeaconState createBeaconState(
      boolean addToList, UInt64 amount, Validator knownValidator) {
    return BeaconState.createEmpty()
        .updated(
            beaconState -> {
              beaconState.setSlot(dataStructureUtil.randomUInt64());
              beaconState.setFork(
                  new Fork(
                      specConstants.getGenesisForkVersion(),
                      specConstants.getGenesisForkVersion(),
                      SpecConstants.GENESIS_EPOCH));

              SSZMutableList<Validator> validatorList =
                  SSZList.createMutable(
                      Arrays.asList(
                          dataStructureUtil.randomValidator(),
                          dataStructureUtil.randomValidator(),
                          dataStructureUtil.randomValidator()),
                      specConstants.getValidatorRegistryLimit(),
                      Validator.class);
              SSZMutableList<UInt64> balanceList =
                  SSZList.createMutable(
                      Arrays.asList(
                          dataStructureUtil.randomUInt64(),
                          dataStructureUtil.randomUInt64(),
                          dataStructureUtil.randomUInt64()),
                      specConstants.getValidatorRegistryLimit(),
                      UInt64.class);

              if (addToList) {
                validatorList.add(knownValidator);
                balanceList.add(amount);
              }

              beaconState.getValidators().addAll(validatorList);
              beaconState.getBalances().addAll(balanceList);
            });
  }

  private BeaconState processDepositHelper(BeaconState beaconState, DepositData depositData)
      throws BlockProcessingException {

    // Add the deposit to a Merkle tree so that we can get the root to put into the state Eth1 data
    MerkleTree depositMerkleTree =
        new OptimizedMerkleTree(specConstants.getDepositContractTreeDepth());
    depositMerkleTree.add(depositData.hashTreeRoot());

    beaconState =
        beaconState.updated(
            state ->
                state.setEth1_data(
                    new Eth1Data(depositMerkleTree.getRoot(), UInt64.valueOf(1), Bytes32.ZERO)));

    SSZMutableList<DepositWithIndex> deposits =
        SSZList.createMutable(DepositWithIndex.class, specConstants.getMaxDeposits());
    deposits.add(
        new DepositWithIndex(depositMerkleTree.getProof(0), depositData, UInt64.valueOf(0)));

    // Attempt to process deposit with above data.
    return beaconState.updated(state -> blockProcessorUtil.processDeposits(state, deposits));
  }

  private Validator makeValidator(BLSPublicKey pubkey, Bytes32 withdrawalCredentials) {
    return new Validator(
        pubkey.toBytesCompressed(),
        withdrawalCredentials,
        specConstants.getMaxEffectiveBalance(),
        false,
        SpecConstants.FAR_FUTURE_EPOCH,
        SpecConstants.FAR_FUTURE_EPOCH,
        SpecConstants.FAR_FUTURE_EPOCH,
        SpecConstants.FAR_FUTURE_EPOCH);
  }
}
