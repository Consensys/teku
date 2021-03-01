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

import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.ssz.backing.SszList;
import tech.pegasys.teku.util.config.Constants;

public class BeaconBlockBodyLists {

  public static SszList<ProposerSlashing> createProposerSlashings(
      ProposerSlashing... proposerSlashings) {
    return BeaconBlockBody.getSszSchema().getProposerSlashingsSchema().of(proposerSlashings);
  }

  public static SszList<AttesterSlashing> createAttesterSlashings(
      AttesterSlashing... attesterSlashings) {
    return BeaconBlockBody.getSszSchema().getAttesterSlashingsSchema().of(attesterSlashings);
  }

  public static SszList<Attestation> createAttestations(Attestation... attestations) {
    return BeaconBlockBody.getSszSchema().getAttestationsSchema().of(attestations);
  }

  public static SszList<Deposit> createDeposits(Deposit... deposits) {
    return BeaconBlockBody.getSszSchema().getDepositsSchema().of(deposits);
  }

  public static SszList<SignedVoluntaryExit> createVoluntaryExits(
      SignedVoluntaryExit... voluntaryExits) {
    return BeaconBlockBody.getSszSchema().getVoluntaryExitsSchema().of(voluntaryExits);
  }
}
