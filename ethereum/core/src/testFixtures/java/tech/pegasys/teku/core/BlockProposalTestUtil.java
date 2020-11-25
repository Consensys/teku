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

package tech.pegasys.teku.core;

import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.get_beacon_proposer_index;
import static tech.pegasys.teku.util.config.Constants.EPOCHS_PER_ETH1_VOTING_PERIOD;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.Hash;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.core.exceptions.EpochProcessingException;
import tech.pegasys.teku.core.exceptions.SlotProcessingException;
import tech.pegasys.teku.core.signatures.Signer;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockBodyLists;
import tech.pegasys.teku.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.Deposit;
import tech.pegasys.teku.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.util.BeaconStateUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;

public class BlockProposalTestUtil {

  private final BlockProposalUtil blockProposalUtil;
  private final StateTransition stateTransition;

  public BlockProposalTestUtil() {
    stateTransition = new StateTransition();
    blockProposalUtil = new BlockProposalUtil(stateTransition);
  }

  public SignedBlockAndState createNewBlock(
      final Signer signer,
      final UInt64 newSlot,
      final BeaconState state,
      final Bytes32 parentBlockSigningRoot,
      final Eth1Data eth1Data,
      final SSZList<Attestation> attestations,
      final SSZList<ProposerSlashing> slashings,
      final SSZList<Deposit> deposits,
      final SSZList<SignedVoluntaryExit> exits)
      throws StateTransitionException, EpochProcessingException, SlotProcessingException {

    final UInt64 newEpoch = compute_epoch_at_slot(newSlot);
    final BLSSignature randaoReveal =
        signer.createRandaoReveal(newEpoch, state.getForkInfo()).join();

    final BeaconState blockSlotState = stateTransition.process_slots(state, newSlot);
    final BeaconBlockAndState newBlockAndState =
        blockProposalUtil.createNewUnsignedBlock(
            newSlot,
            get_beacon_proposer_index(blockSlotState, newSlot),
            randaoReveal,
            blockSlotState,
            parentBlockSigningRoot,
            eth1Data,
            Bytes32.ZERO,
            attestations,
            slashings,
            BeaconBlockBodyLists.createAttesterSlashings(),
            deposits,
            exits);

    // Sign block and set block signature
    final BeaconBlock block = newBlockAndState.getBlock();
    BLSSignature blockSignature = signer.signBlock(block, state.getForkInfo()).join();

    final SignedBeaconBlock signedBlock = new SignedBeaconBlock(block, blockSignature);
    return new SignedBlockAndState(signedBlock, newBlockAndState.getState());
  }

  public SignedBlockAndState createBlock(
      final Signer signer,
      final UInt64 newSlot,
      final BeaconState previousState,
      final Bytes32 parentBlockSigningRoot,
      final Optional<SSZList<Attestation>> attestations,
      final Optional<SSZList<Deposit>> deposits,
      final Optional<SSZList<SignedVoluntaryExit>> exits,
      final Optional<Eth1Data> eth1Data)
      throws StateTransitionException, EpochProcessingException, SlotProcessingException {
    final UInt64 newEpoch = compute_epoch_at_slot(newSlot);
    return createNewBlock(
        signer,
        newSlot,
        previousState,
        parentBlockSigningRoot,
        eth1Data.orElse(get_eth1_data_stub(previousState, newEpoch)),
        attestations.orElse(BeaconBlockBodyLists.createAttestations()),
        BeaconBlockBodyLists.createProposerSlashings(),
        deposits.orElse(BeaconBlockBodyLists.createDeposits()),
        exits.orElse(BeaconBlockBodyLists.createVoluntaryExits()));
  }

  private static Eth1Data get_eth1_data_stub(BeaconState state, UInt64 current_epoch) {
    UInt64 epochs_per_period = UInt64.valueOf(EPOCHS_PER_ETH1_VOTING_PERIOD);
    UInt64 voting_period = current_epoch.dividedBy(epochs_per_period);
    return new Eth1Data(
        Hash.sha2_256(SSZ.encodeUInt64(epochs_per_period.longValue())),
        state.getEth1_deposit_index(),
        Hash.sha2_256(Hash.sha2_256(SSZ.encodeUInt64(voting_period.longValue()))));
  }

  public int getProposerIndexForSlot(final BeaconState preState, final UInt64 slot) {
    BeaconState state;
    try {
      state = stateTransition.process_slots(preState, slot);
    } catch (SlotProcessingException | EpochProcessingException e) {
      throw new RuntimeException(e);
    }
    return BeaconStateUtil.get_beacon_proposer_index(state);
  }
}
