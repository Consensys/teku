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

package tech.pegasys.teku.spec.logic.common.util;

import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.bls.BLSSignatureVerifier.InvalidSignatureException;
import tech.pegasys.teku.spec.cache.IndexedAttestationCache;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSummary;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;

public interface BlockProcessorUtil {
  void processBlockHeader(MutableBeaconState state, BeaconBlockSummary blockHeader)
      throws BlockProcessingException;

  void processRandaoNoValidation(MutableBeaconState state, BeaconBlockBody body)
      throws BlockProcessingException;

  void verifyRandao(BeaconState state, BeaconBlock block, BLSSignatureVerifier bls)
      throws InvalidSignatureException;

  void processEth1Data(MutableBeaconState state, BeaconBlockBody body);

  boolean isEnoughVotesToUpdateEth1Data(long voteCount);

  long getVoteCount(BeaconState state, Eth1Data eth1Data);

  void processOperationsNoValidation(MutableBeaconState state, BeaconBlockBody body)
      throws BlockProcessingException;

  void processProposerSlashings(
      MutableBeaconState state, SSZList<ProposerSlashing> proposerSlashings)
      throws BlockProcessingException;

  void processProposerSlashingsNoValidation(
      MutableBeaconState state, SSZList<ProposerSlashing> proposerSlashings)
      throws BlockProcessingException;

  boolean verifyProposerSlashings(
      BeaconState state,
      SSZList<ProposerSlashing> proposerSlashings,
      BLSSignatureVerifier signatureVerifier);

  void processAttesterSlashings(
      MutableBeaconState state, SSZList<AttesterSlashing> attesterSlashings)
      throws BlockProcessingException;

  void processAttestations(MutableBeaconState state, SSZList<Attestation> attestations)
      throws BlockProcessingException;

  void processAttestations(
      MutableBeaconState state,
      SSZList<Attestation> attestations,
      IndexedAttestationCache indexedAttestationCache)
      throws BlockProcessingException;

  void processAttestationsNoValidation(MutableBeaconState state, SSZList<Attestation> attestations)
      throws BlockProcessingException;

  void verifyAttestations(
      BeaconState state,
      SSZList<Attestation> attestations,
      BLSSignatureVerifier signatureVerifier,
      IndexedAttestationCache indexedAttestationCache)
      throws BlockProcessingException;

  void processDeposits(MutableBeaconState state, SSZList<? extends Deposit> deposits)
      throws BlockProcessingException;

  void processVoluntaryExits(MutableBeaconState state, SSZList<SignedVoluntaryExit> exits)
      throws BlockProcessingException;

  void processVoluntaryExitsNoValidation(
      MutableBeaconState state, SSZList<SignedVoluntaryExit> exits) throws BlockProcessingException;

  boolean verifyVoluntaryExits(
      BeaconState state,
      SSZList<SignedVoluntaryExit> exits,
      BLSSignatureVerifier signatureVerifier);
}
