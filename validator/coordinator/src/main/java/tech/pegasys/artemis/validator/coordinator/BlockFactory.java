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

package tech.pegasys.artemis.validator.coordinator;

import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.core.StateTransition;
import tech.pegasys.artemis.core.StateTransitionException;
import tech.pegasys.artemis.core.exceptions.EpochProcessingException;
import tech.pegasys.artemis.core.exceptions.SlotProcessingException;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockBodyLists;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.operations.ProposerSlashing;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.statetransition.BlockProposalUtil;
import tech.pegasys.artemis.statetransition.attestation.AggregatingAttestationPool;
import tech.pegasys.artemis.util.SSZTypes.SSZList;
import tech.pegasys.artemis.util.bls.BLSSignature;

public class BlockFactory {
  private final BlockProposalUtil blockCreator;
  private final StateTransition stateTransition;
  private final AggregatingAttestationPool attestationPool;
  private final DepositProvider depositProvider;
  private final Eth1DataCache eth1DataCache;

  public BlockFactory(
      final BlockProposalUtil blockCreator,
      final StateTransition stateTransition,
      final AggregatingAttestationPool attestationPool,
      final DepositProvider depositProvider,
      final Eth1DataCache eth1DataCache) {
    this.blockCreator = blockCreator;
    this.stateTransition = stateTransition;
    this.attestationPool = attestationPool;
    this.depositProvider = depositProvider;
    this.eth1DataCache = eth1DataCache;
  }

  public BeaconBlock createUnsignedBlock(
      final BeaconState previousState,
      final BeaconBlock previousBlock,
      final UnsignedLong newSlot,
      final BLSSignature randaoReveal)
      throws EpochProcessingException, SlotProcessingException, StateTransitionException {

    // Process empty slots up to the new slot
    BeaconState newState = stateTransition.process_slots(previousState, newSlot);

    // Collect attestations to include
    SSZList<Attestation> attestations = attestationPool.getAttestationsForBlock(newSlot);
    // Collect slashing to include
    final SSZList<ProposerSlashing> slashingsInBlock =
        BeaconBlockBodyLists.createProposerSlashings();
    // Collect deposits
    final SSZList<Deposit> deposits = depositProvider.getDeposits(newState);

    Eth1Data eth1Data = eth1DataCache.get_eth1_vote(newState);
    final Bytes32 parentRoot = previousBlock.hash_tree_root();

    return blockCreator.createNewUnsignedBlock(
        newSlot,
        randaoReveal,
        newState,
        parentRoot,
        eth1Data,
        attestations,
        slashingsInBlock,
        deposits);
  }
}
