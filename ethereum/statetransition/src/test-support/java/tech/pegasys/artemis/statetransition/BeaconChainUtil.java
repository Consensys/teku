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

package tech.pegasys.artemis.statetransition;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.primitives.UnsignedLong;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.validator.Signer;
import tech.pegasys.artemis.statetransition.util.StartupUtil;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.util.bls.BLSKeyGenerator;
import tech.pegasys.artemis.util.bls.BLSKeyPair;
import tech.pegasys.artemis.util.bls.BLSSignature;

public class BeaconChainUtil {

  private final StateTransition stateTransition = new StateTransition(false);
  private final BlockProposalUtil blockCreator = new BlockProposalUtil(stateTransition);
  private final ChainStorageClient storageClient;
  private final List<BLSKeyPair> validatorKeys;

  private BeaconChainUtil(
      final List<BLSKeyPair> validatorKeys, final ChainStorageClient chainStorageClient) {
    this.validatorKeys = validatorKeys;
    this.storageClient = chainStorageClient;
    initializeStorage(chainStorageClient);
  }

  public static BeaconChainUtil create(
      final int validatorCount, final ChainStorageClient storageClient) {
    final List<BLSKeyPair> validatorKeys = BLSKeyGenerator.generateKeyPairs(validatorCount);
    return new BeaconChainUtil(validatorKeys, storageClient);
  }

  public static void initializeStorage(
      final ChainStorageClient chainStorageClient, final List<BLSKeyPair> validatorKeys) {
    StartupUtil.setupInitialState(chainStorageClient, 0, null, validatorKeys);
  }

  public void initializeStorage(final ChainStorageClient chainStorageClient) {
    initializeStorage(chainStorageClient, validatorKeys);
  }

  public BeaconBlock createBlockAtSlot(final UnsignedLong slot) throws Exception {
    return createBlockAtSlot(slot, true);
  }

  public BeaconBlock createBlockAtSlotFromInvalidProposer(final UnsignedLong slot)
      throws Exception {
    return createBlockAtSlot(slot, false);
  }

  private BeaconBlock createBlockAtSlot(final UnsignedLong slot, boolean withValidProposer)
      throws Exception {
    checkState(
        withValidProposer || validatorKeys.size() > 1,
        "Must have >1 validator in order to create a block from an invalid proposer.");
    final Bytes32 bestBlockRoot = storageClient.getBestBlockRoot();
    final BeaconBlock bestBlock = storageClient.getStore().getBlock(bestBlockRoot);
    final BeaconState preState = storageClient.getBestBlockRootState();
    checkArgument(bestBlock.getSlot().compareTo(slot) < 0, "Slot must be in the future.");

    final int correctProposerIndex = blockCreator.getProposerIndexForSlot(preState, slot);
    final int proposerIndex =
        withValidProposer ? correctProposerIndex : getWrongProposerIndex(correctProposerIndex);

    final Signer signer = getSigner(proposerIndex);
    return blockCreator.createEmptyBlock(signer, slot, preState, bestBlockRoot);
  }

  public int getWrongProposerIndex(final int actualProposerIndex) {
    return actualProposerIndex == 0 ? 1 : actualProposerIndex - 1;
  }

  private Signer getSigner(final int proposerIndex) {
    BLSKeyPair proposerKey = validatorKeys.get(proposerIndex);
    return (message, domain) -> BLSSignature.sign(proposerKey, message, domain);
  }
}
