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

import com.google.common.primitives.UnsignedLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.bls.BLSPublicKey;
import tech.pegasys.artemis.bls.BLSSignature;
import tech.pegasys.artemis.core.StateTransition;
import tech.pegasys.artemis.core.StateTransitionException;
import tech.pegasys.artemis.core.exceptions.EpochProcessingException;
import tech.pegasys.artemis.core.exceptions.SlotProcessingException;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockBody;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.operations.ProposerSlashing;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.util.BeaconStateUtil;
import tech.pegasys.artemis.ssz.SSZTypes.SSZList;

public class BlockProposalUtil {

  private static final Logger LOG = LogManager.getLogger();

  private final StateTransition stateTransition;

  public BlockProposalUtil(final StateTransition stateTransition) {
    this.stateTransition = stateTransition;
  }

  public BeaconBlock createNewUnsignedBlock(
      final UnsignedLong newSlot,
      final BLSSignature randaoReveal,
      final BeaconState state,
      final Bytes32 parentBlockSigningRoot,
      final Eth1Data eth1Data,
      final SSZList<Attestation> attestations,
      final SSZList<ProposerSlashing> slashings,
      final SSZList<Deposit> deposits)
      throws StateTransitionException {
    // Create block body
    BeaconBlockBody beaconBlockBody = new BeaconBlockBody();
    beaconBlockBody.setEth1_data(eth1Data);
    beaconBlockBody.setDeposits(deposits);
    beaconBlockBody.setAttestations(attestations);
    beaconBlockBody.setProposer_slashings(slashings);
    beaconBlockBody.setRandao_reveal(randaoReveal);

    // Create initial block with some stubs
    final Bytes32 tmpStateRoot = Bytes32.ZERO;
    BeaconBlock newBlock =
        new BeaconBlock(newSlot, parentBlockSigningRoot, tmpStateRoot, beaconBlockBody);

    // Run state transition and set state root
    Bytes32 stateRoot =
        stateTransition
            .initiate(state, new SignedBeaconBlock(newBlock, BLSSignature.empty()), false)
            .hash_tree_root();
    newBlock.setState_root(stateRoot);

    return newBlock;
  }

  public BLSPublicKey getProposerForSlot(final BeaconState preState, final UnsignedLong slot) {
    int proposerIndex = getProposerIndexForSlot(preState, slot);
    return preState.getValidators().get(proposerIndex).getPubkey();
  }

  public int getProposerIndexForSlot(final BeaconState preState, final UnsignedLong slot) {
    BeaconState state = preState;
    try {
      state = stateTransition.process_slots(preState, slot);
    } catch (SlotProcessingException | EpochProcessingException e) {
      LOG.fatal("Coordinator checking proposer index exception", e);
    }
    return BeaconStateUtil.get_beacon_proposer_index(state);
  }
}
