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

package tech.pegasys.teku.reference.phase0.operations;

import tech.pegasys.teku.datastructures.blocks.BeaconBlockSummary;
import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.datastructures.operations.Deposit;
import tech.pegasys.teku.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.datastructures.state.MutableBeaconState;
import tech.pegasys.teku.spec.SpecProvider;
import tech.pegasys.teku.spec.statetransition.exceptions.BlockProcessingException;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;

public class DefaultOperationProcessor implements OperationProcessor {
  private final SpecProvider specProvider;

  public DefaultOperationProcessor(final SpecProvider specProvider) {
    this.specProvider = specProvider;
  }

  @Override
  public void processAttesterSlashing(
      final MutableBeaconState state, final AttesterSlashing attesterSlashings)
      throws BlockProcessingException {
    specProvider.processAttesterSlashings(state, SSZList.singleton(attesterSlashings));
  }

  @Override
  public void processProposerSlashing(
      final MutableBeaconState state, final ProposerSlashing proposerSlashing)
      throws BlockProcessingException {
    specProvider.processProposerSlashings(state, SSZList.singleton(proposerSlashing));
  }

  @Override
  public void processBlockHeader(
      final MutableBeaconState state, final BeaconBlockSummary blockHeader)
      throws BlockProcessingException {
    specProvider.processBlockHeader(state, blockHeader);
  }

  @Override
  public void processDeposit(final MutableBeaconState state, final Deposit deposit)
      throws BlockProcessingException {
    specProvider.processDeposits(state, SSZList.singleton(deposit));
  }

  @Override
  public void processVoluntaryExit(
      final MutableBeaconState state, final SignedVoluntaryExit voluntaryExit)
      throws BlockProcessingException {
    specProvider.processVoluntaryExits(state, SSZList.singleton(voluntaryExit));
  }

  @Override
  public void processAttestation(final MutableBeaconState state, final Attestation attestation)
      throws BlockProcessingException {
    specProvider.processAttestations(state, SSZList.singleton(attestation));
  }
}
