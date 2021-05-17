/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.spec.logic.common.block;

import java.util.Map;
import java.util.Optional;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.spec.cache.IndexedAttestationCache;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSummary;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationInvalidReason;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.StateTransitionException;
import tech.pegasys.teku.ssz.SszList;

public interface BlockProcessor {
  Optional<OperationInvalidReason> validateAttestation(
      final BeaconState state, final AttestationData data);

  /**
   * Processes the given block on top of {@code blockSlotState} and optionally validates the block
   *
   * @param signedBlock The block to be processed
   * @param blockSlotState The preState on which this block should be procssed, this preState must
   *     already be advanced to the block's slot
   * @param validateStateRootAndSignatures Whether to run signature and state root validations
   * @param indexedAttestationCache A cache of indexed attestations
   * @return The post state after processing the block on top of {@code blockSlotState}
   * @throws StateTransitionException If the block is invalid or cannot be processed
   */
  BeaconState processAndValidateBlock(
      final SignedBeaconBlock signedBlock,
      final BeaconState blockSlotState,
      final boolean validateStateRootAndSignatures,
      final IndexedAttestationCache indexedAttestationCache)
      throws StateTransitionException;

  BeaconState processBlock(
      BeaconState preState, BeaconBlock block, IndexedAttestationCache indexedAttestationCache)
      throws BlockProcessingException;

  void processBlockHeader(MutableBeaconState state, BeaconBlockSummary blockHeader)
      throws BlockProcessingException;

  boolean isEnoughVotesToUpdateEth1Data(long voteCount);

  long getVoteCount(BeaconState state, Eth1Data eth1Data);

  void processProposerSlashings(
      MutableBeaconState state, SszList<ProposerSlashing> proposerSlashings)
      throws BlockProcessingException;

  void processAttesterSlashings(
      MutableBeaconState state, SszList<AttesterSlashing> attesterSlashings)
      throws BlockProcessingException;

  void processAttestations(MutableBeaconState state, SszList<Attestation> attestations)
      throws BlockProcessingException;

  void processDeposits(MutableBeaconState state, SszList<? extends Deposit> deposits)
      throws BlockProcessingException;

  void processDepositWithoutCheckingMerkleProof(
      final MutableBeaconState state,
      final Deposit deposit,
      final Map<BLSPublicKey, Integer> pubKeyToIndexMap);

  void processVoluntaryExits(MutableBeaconState state, SszList<SignedVoluntaryExit> exits)
      throws BlockProcessingException;

  void processSyncCommittee(MutableBeaconState state, SyncAggregate syncAggregate)
      throws BlockProcessingException;
}
