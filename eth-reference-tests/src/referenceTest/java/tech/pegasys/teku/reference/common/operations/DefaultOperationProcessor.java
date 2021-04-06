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

package tech.pegasys.teku.reference.common.operations;

import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSummary;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;

public class DefaultOperationProcessor implements OperationProcessor {
  private final Spec spec;
  private final BeaconBlockBodySchema<?> beaconBlockBodySchema;

  public DefaultOperationProcessor(final Spec spec) {
    this.spec = spec;
    this.beaconBlockBodySchema =
        spec.getGenesisSpec().getSchemaDefinitions().getBeaconBlockBodySchema();
  }

  @Override
  public void processAttesterSlashing(
      final MutableBeaconState state, final AttesterSlashing attesterSlashings)
      throws BlockProcessingException {
    spec.processAttesterSlashings(
        state, beaconBlockBodySchema.getAttesterSlashingsSchema().of(attesterSlashings));
  }

  @Override
  public void processProposerSlashing(
      final MutableBeaconState state, final ProposerSlashing proposerSlashing)
      throws BlockProcessingException {
    spec.processProposerSlashings(
        state, beaconBlockBodySchema.getProposerSlashingsSchema().of(proposerSlashing));
  }

  @Override
  public void processBlockHeader(
      final MutableBeaconState state, final BeaconBlockSummary blockHeader)
      throws BlockProcessingException {
    spec.processBlockHeader(state, blockHeader);
  }

  @Override
  public void processDeposit(final MutableBeaconState state, final Deposit deposit)
      throws BlockProcessingException {
    spec.processDeposits(state, beaconBlockBodySchema.getDepositsSchema().of(deposit));
  }

  @Override
  public void processVoluntaryExit(
      final MutableBeaconState state, final SignedVoluntaryExit voluntaryExit)
      throws BlockProcessingException {
    spec.processVoluntaryExits(
        state, beaconBlockBodySchema.getVoluntaryExitsSchema().of(voluntaryExit));
  }

  @Override
  public void processAttestation(final MutableBeaconState state, final Attestation attestation)
      throws BlockProcessingException {
    spec.processAttestations(state, beaconBlockBodySchema.getAttestationsSchema().of(attestation));
  }

  @Override
  public void processSyncCommittee(final MutableBeaconState state, final SyncAggregate aggregate)
      throws BlockProcessingException {
    spec.atSlot(state.getSlot()).getBlockProcessor().processSyncCommittee(state, aggregate);
  }
}
