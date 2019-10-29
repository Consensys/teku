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

package org.ethereum.beacon.chain.observer;

import java.util.List;
import org.ethereum.beacon.core.operations.Attestation;
import org.ethereum.beacon.core.operations.ProposerSlashing;
import org.ethereum.beacon.core.operations.Transfer;
import org.ethereum.beacon.core.operations.VoluntaryExit;
import org.ethereum.beacon.core.operations.slashing.AttesterSlashing;
import org.ethereum.beacon.core.spec.SpecConstants;

/** A pending state interface. */
public interface PendingOperations {

  List<Attestation> getAttestations();

  List<ProposerSlashing> peekProposerSlashings(int maxCount);

  List<AttesterSlashing> peekAttesterSlashings(int maxCount);

  List<Attestation> peekAggregateAttestations(int maxCount, SpecConstants specConstants);

  List<VoluntaryExit> peekExits(int maxCount);

  List<Transfer> peekTransfers(int maxCount);

  default String toStringShort() {
    return "PendingOperations["
        + (getAttestations().isEmpty() ? "" : "attest: " + getAttestations().size())
        + "]";
  }

  default String toStringMedium(SpecConstants spec) {
    String ret = "PendingOperations[";
    if (!getAttestations().isEmpty()) {
      ret += "attest (slot/shard/beaconBlock): [";
      for (Attestation att : getAttestations()) {
        ret += att.toStringShort(spec) + ", ";
      }
      ret = ret.substring(0, ret.length() - 2);
    }
    ret += "]";
    return ret;
  }
}
