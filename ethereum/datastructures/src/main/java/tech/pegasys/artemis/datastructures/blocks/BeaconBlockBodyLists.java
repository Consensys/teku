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

package tech.pegasys.artemis.datastructures.blocks;

import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.AttesterSlashing;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.operations.ProposerSlashing;
import tech.pegasys.artemis.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.artemis.util.SSZTypes.SSZList;
import tech.pegasys.artemis.util.config.Constants;

public class BeaconBlockBodyLists {

  public static SSZList<ProposerSlashing> createProposerSlashings() {
    return SSZList.create(ProposerSlashing.class, Constants.MAX_PROPOSER_SLASHINGS);
  }

  public static SSZList<AttesterSlashing> createAttesterSlashings() {
    return SSZList.create(AttesterSlashing.class, Constants.MAX_ATTESTER_SLASHINGS);
  }

  public static SSZList<Attestation> createAttestations() {
    return SSZList.create(Attestation.class, Constants.MAX_ATTESTATIONS);
  }

  public static SSZList<Deposit> createDeposits() {
    return SSZList.create(Deposit.class, Constants.MAX_DEPOSITS);
  }

  public static SSZList<SignedVoluntaryExit> createVoluntaryExits() {
    return SSZList.create(SignedVoluntaryExit.class, Constants.MAX_VOLUNTARY_EXITS);
  }
}
