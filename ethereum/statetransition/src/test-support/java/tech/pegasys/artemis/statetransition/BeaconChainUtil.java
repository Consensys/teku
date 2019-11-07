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
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_beacon_proposer_index;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_current_epoch;
import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.get_domain;
import static tech.pegasys.artemis.statetransition.util.StartupUtil.get_eth1_data_stub;
import static tech.pegasys.artemis.util.config.Constants.DOMAIN_BEACON_PROPOSER;
import static tech.pegasys.artemis.util.config.Constants.MAX_ATTESTATIONS;
import static tech.pegasys.artemis.util.config.Constants.MAX_DEPOSITS;

import com.google.common.primitives.UnsignedLong;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockBody;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.BeaconStateWithCache;
import tech.pegasys.artemis.statetransition.util.EpochProcessingException;
import tech.pegasys.artemis.statetransition.util.SlotProcessingException;
import tech.pegasys.artemis.statetransition.util.StartupUtil;
import tech.pegasys.artemis.storage.ChainStorageClient;
import tech.pegasys.artemis.util.SSZTypes.SSZList;
import tech.pegasys.artemis.util.bls.BLSKeyGenerator;
import tech.pegasys.artemis.util.bls.BLSKeyPair;
import tech.pegasys.artemis.util.bls.BLSSignature;

public class BeaconChainUtil {

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

  public BeaconBlock createBlockAtSlot(final UnsignedLong slot)
      throws EpochProcessingException, SlotProcessingException {
    return createBlockAtSlot(slot, true);
  }

  public BeaconBlock createBlockAtSlotFromInvalidProposer(final UnsignedLong slot)
      throws EpochProcessingException, SlotProcessingException {
    return createBlockAtSlot(slot, false);
  }

  private BeaconBlock createBlockAtSlot(final UnsignedLong slot, boolean withValidProposer)
      throws EpochProcessingException, SlotProcessingException {
    checkState(
        withValidProposer || validatorKeys.size() > 1,
        "Must have >1 validator in order to create a block from an invalid proposer.");
    final Bytes32 bestBlockRoot = storageClient.getBestBlockRoot();
    final BeaconBlock bestBlock = storageClient.getStore().getBlock(bestBlockRoot);
    final BeaconState preState = storageClient.getBestBlockRootState();
    checkArgument(bestBlock.getSlot().compareTo(slot) < 0, "Slot must be in the future.");

    final BeaconStateWithCache postState = BeaconStateWithCache.fromBeaconState(preState);
    final StateTransition stateTransition = new StateTransition(false);
    stateTransition.process_slots(postState, slot, false);

    final int correctProposerIndex = get_beacon_proposer_index(postState);
    final int proposerIndex =
        withValidProposer
            ? correctProposerIndex
            : (correctProposerIndex + 1) % postState.getValidators().size();

    final SSZList<Deposit> deposits = new SSZList<>(Deposit.class, MAX_DEPOSITS);
    final SSZList<Attestation> attestations = new SSZList<>(Attestation.class, MAX_ATTESTATIONS);

    BeaconBlock newBlock =
        createBeaconBlock(postState, bestBlock.signing_root("signature"), deposits, attestations);
    BLSKeyPair proposerKey = validatorKeys.get(proposerIndex);
    UnsignedLong epoch = get_current_epoch(postState);
    Bytes domain = get_domain(postState, DOMAIN_BEACON_PROPOSER, epoch);
    newBlock.setSignature(
        BLSSignature.sign(proposerKey, newBlock.signing_root("signature"), domain));

    return newBlock;
  }

  private BeaconBlock createBeaconBlock(
      BeaconState postState,
      Bytes32 parentBlockRoot,
      SSZList<Deposit> deposits,
      SSZList<Attestation> attestations) {

    final Bytes32 stateRoot = postState.hash_tree_root();
    BeaconBlockBody beaconBlockBody = new BeaconBlockBody();
    UnsignedLong slot = postState.getSlot();
    beaconBlockBody.setEth1_data(get_eth1_data_stub(postState, compute_epoch_at_slot(slot)));
    beaconBlockBody.setDeposits(deposits);
    beaconBlockBody.setAttestations(attestations);
    return new BeaconBlock(slot, parentBlockRoot, stateRoot, beaconBlockBody, BLSSignature.empty());
  }
}
