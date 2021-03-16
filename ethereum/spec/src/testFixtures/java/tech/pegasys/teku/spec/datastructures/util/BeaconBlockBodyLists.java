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

package tech.pegasys.teku.spec.datastructures.util;

import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.ssz.backing.SszList;

public class BeaconBlockBodyLists {

  public static BeaconBlockBodyLists ofSpec(Spec spec) {
    return new BeaconBlockBodyLists(spec);
  }

  private final BeaconBlockBodySchema<?> blockBodySchema;

  public BeaconBlockBodyLists(Spec spec) {
    blockBodySchema = spec.getGenesisSpec().getSchemaDefinitions().getBeaconBlockBodySchema();
  }

  public SszList<ProposerSlashing> createProposerSlashings(ProposerSlashing... proposerSlashings) {
    return blockBodySchema.getProposerSlashingsSchema().of(proposerSlashings);
  }

  public SszList<AttesterSlashing> createAttesterSlashings(AttesterSlashing... attesterSlashings) {
    return blockBodySchema.getAttesterSlashingsSchema().of(attesterSlashings);
  }

  public SszList<Attestation> createAttestations(Attestation... attestations) {
    return blockBodySchema.getAttestationsSchema().of(attestations);
  }

  public SszList<Deposit> createDeposits(Deposit... deposits) {
    return blockBodySchema.getDepositsSchema().of(deposits);
  }

  public SszList<SignedVoluntaryExit> createVoluntaryExits(SignedVoluntaryExit... voluntaryExits) {
    return blockBodySchema.getVoluntaryExitsSchema().of(voluntaryExits);
  }
}
