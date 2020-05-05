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

package tech.pegasys.teku.datastructures.blocks;

import tech.pegasys.teku.datastructures.operations.Attestation;
import tech.pegasys.teku.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.datastructures.operations.Deposit;
import tech.pegasys.teku.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;
import tech.pegasys.teku.ssz.SSZTypes.SSZMutableList;
import tech.pegasys.teku.util.config.Constants;

public class BeaconBlockBodyLists {

  public static SSZMutableList<ProposerSlashing> createProposerSlashings() {
    return SSZList.createMutable(ProposerSlashing.class, Constants.MAX_PROPOSER_SLASHINGS);
  }

  public static SSZMutableList<AttesterSlashing> createAttesterSlashings() {
    return SSZList.createMutable(AttesterSlashing.class, Constants.MAX_ATTESTER_SLASHINGS);
  }

  public static SSZMutableList<Attestation> createAttestations() {
    return SSZList.createMutable(Attestation.class, Constants.MAX_ATTESTATIONS);
  }

  public static SSZMutableList<Deposit> createDeposits() {
    return SSZList.createMutable(Deposit.class, Constants.MAX_DEPOSITS);
  }

  public static SSZMutableList<SignedVoluntaryExit> createVoluntaryExits() {
    return SSZList.createMutable(SignedVoluntaryExit.class, Constants.MAX_VOLUNTARY_EXITS);
  }
}
