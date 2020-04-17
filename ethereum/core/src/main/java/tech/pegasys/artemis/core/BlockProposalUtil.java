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

package tech.pegasys.artemis.core;

import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.bls.BLSSignature;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockAndState;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockBody;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.operations.ProposerSlashing;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.ssz.SSZTypes.SSZList;

public class BlockProposalUtil {

  private final StateTransition stateTransition;

  public BlockProposalUtil(final StateTransition stateTransition) {
    this.stateTransition = stateTransition;
  }

  public BeaconBlockAndState createNewUnsignedBlock(
      final UnsignedLong newSlot,
      final int proposerIndex,
      final BLSSignature randaoReveal,
      final BeaconState preState,
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
        new BeaconBlock(
            newSlot,
            UnsignedLong.valueOf(proposerIndex),
            parentBlockSigningRoot,
            tmpStateRoot,
            beaconBlockBody);

    // Run state transition and set state root
    final BeaconState newState =
        stateTransition.initiate(
            preState, new SignedBeaconBlock(newBlock, BLSSignature.empty()), false);

    Bytes32 stateRoot = newState.hash_tree_root();
    newBlock.setState_root(stateRoot);

    return new BeaconBlockAndState(newBlock, newState);
  }
}
