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

package tech.pegasys.artemis.core;

import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.artemis.util.config.Constants.EPOCHS_PER_ETH1_VOTING_PERIOD;

import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.Hash;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.artemis.bls.BLSSignature;
import tech.pegasys.artemis.core.exceptions.EpochProcessingException;
import tech.pegasys.artemis.core.exceptions.SlotProcessingException;
import tech.pegasys.artemis.core.signatures.MessageSignerService;
import tech.pegasys.artemis.core.signatures.Signer;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockBodyLists;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.operations.ProposerSlashing;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.util.BeaconStateUtil;
import tech.pegasys.artemis.ssz.SSZTypes.SSZList;

public class BlockProposalTestUtil {

  private final BlockProposalUtil blockProposalUtil;
  private final StateTransition stateTransition;

  public BlockProposalTestUtil() {
    stateTransition = new StateTransition();
    blockProposalUtil = new BlockProposalUtil(stateTransition);
  }

  public SignedBlockAndState createNewBlock(
      final MessageSignerService signer,
      final UnsignedLong newSlot,
      final BeaconState state,
      final Bytes32 parentBlockSigningRoot,
      final Eth1Data eth1Data,
      final SSZList<Attestation> attestations,
      final SSZList<ProposerSlashing> slashings,
      final SSZList<Deposit> deposits)
      throws StateTransitionException {

    final UnsignedLong newEpoch = compute_epoch_at_slot(newSlot);
    final BLSSignature randaoReveal =
        new Signer(signer).createRandaoReveal(newEpoch, state.getForkInfo()).join();

    final BeaconBlockAndState newBlockAndState =
        blockProposalUtil.createNewUnsignedBlock(
            newSlot,
            getProposerIndexForSlot(state, newSlot),
            randaoReveal,
            state,
            parentBlockSigningRoot,
            eth1Data,
            attestations,
            slashings,
            deposits);

    // Sign block and set block signature
    final BeaconBlock block = newBlockAndState.getBlock();
    BLSSignature blockSignature = new Signer(signer).signBlock(block, state.getForkInfo()).join();

    final SignedBeaconBlock signedBlock = new SignedBeaconBlock(block, blockSignature);
    return new SignedBlockAndState(signedBlock, newBlockAndState.getState());
  }

  public SignedBlockAndState createEmptyBlock(
      final MessageSignerService signer,
      final UnsignedLong newSlot,
      final BeaconState previousState,
      final Bytes32 parentBlockRoot)
      throws StateTransitionException {
    final UnsignedLong newEpoch = compute_epoch_at_slot(newSlot);
    return createNewBlock(
        signer,
        newSlot,
        previousState,
        parentBlockRoot,
        get_eth1_data_stub(previousState, newEpoch),
        BeaconBlockBodyLists.createAttestations(),
        BeaconBlockBodyLists.createProposerSlashings(),
        BeaconBlockBodyLists.createDeposits());
  }

  public SignedBlockAndState createBlockWithAttestations(
      final MessageSignerService signer,
      final UnsignedLong newSlot,
      final BeaconState previousState,
      final Bytes32 parentBlockSigningRoot,
      final SSZList<Attestation> attestations)
      throws StateTransitionException {
    final UnsignedLong newEpoch = compute_epoch_at_slot(newSlot);
    return createNewBlock(
        signer,
        newSlot,
        previousState,
        parentBlockSigningRoot,
        get_eth1_data_stub(previousState, newEpoch),
        attestations,
        BeaconBlockBodyLists.createProposerSlashings(),
        BeaconBlockBodyLists.createDeposits());
  }

  private static Eth1Data get_eth1_data_stub(BeaconState state, UnsignedLong current_epoch) {
    UnsignedLong epochs_per_period = UnsignedLong.valueOf(EPOCHS_PER_ETH1_VOTING_PERIOD);
    UnsignedLong voting_period = current_epoch.dividedBy(epochs_per_period);
    return new Eth1Data(
        Hash.sha2_256(SSZ.encodeUInt64(epochs_per_period.longValue())),
        state.getEth1_deposit_index(),
        Hash.sha2_256(Hash.sha2_256(SSZ.encodeUInt64(voting_period.longValue()))));
  }

  public int getProposerIndexForSlot(final BeaconState preState, final UnsignedLong slot) {
    BeaconState state;
    try {
      state = stateTransition.process_slots(preState, slot);
    } catch (SlotProcessingException | EpochProcessingException e) {
      throw new RuntimeException(e);
    }
    return BeaconStateUtil.get_beacon_proposer_index(state);
  }
}
