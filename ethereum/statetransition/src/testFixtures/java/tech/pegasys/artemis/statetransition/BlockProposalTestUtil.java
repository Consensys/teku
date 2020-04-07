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

package tech.pegasys.artemis.statetransition;

import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;

import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.core.StateTransition;
import tech.pegasys.artemis.core.StateTransitionException;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockBodyLists;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.operations.ProposerSlashing;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.validator.MessageSignerService;
import tech.pegasys.artemis.statetransition.util.StartupUtil;
import tech.pegasys.artemis.ssz.SSZTypes.SSZList;
import tech.pegasys.artemis.bls.bls.BLSSignature;
import tech.pegasys.artemis.validator.client.signer.Signer;

public class BlockProposalTestUtil {

  private final BlockProposalUtil blockProposalUtil;

  public BlockProposalTestUtil(final StateTransition stateTransition) {
    blockProposalUtil = new BlockProposalUtil(stateTransition);
  }

  public SignedBeaconBlock createNewBlock(
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
        new Signer(signer).createRandaoReveal(newEpoch, state.getFork()).join();

    final BeaconBlock newBlock =
        blockProposalUtil.createNewUnsignedBlock(
            newSlot,
            randaoReveal,
            state,
            parentBlockSigningRoot,
            eth1Data,
            attestations,
            slashings,
            deposits);

    // Sign block and set block signature
    BLSSignature blockSignature = new Signer(signer).signBlock(newBlock, state.getFork()).join();

    return new SignedBeaconBlock(newBlock, blockSignature);
  }

  public SignedBeaconBlock createEmptyBlock(
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
        StartupUtil.get_eth1_data_stub(previousState, newEpoch),
        BeaconBlockBodyLists.createAttestations(),
        BeaconBlockBodyLists.createProposerSlashings(),
        BeaconBlockBodyLists.createDeposits());
  }

  public SignedBeaconBlock createBlockWithAttestations(
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
        StartupUtil.get_eth1_data_stub(previousState, newEpoch),
        attestations,
        BeaconBlockBodyLists.createProposerSlashings(),
        BeaconBlockBodyLists.createDeposits());
  }

  public int getProposerIndexForSlot(final BeaconState preState, final UnsignedLong slot) {
    return blockProposalUtil.getProposerIndexForSlot(preState, slot);
  }
}
