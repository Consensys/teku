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

package org.ethereum.beacon.consensus.transition;

import java.util.ArrayList;
import java.util.List;
import org.ethereum.beacon.consensus.BeaconStateEx;
import org.ethereum.beacon.core.types.Gwei;
import org.ethereum.beacon.core.types.ValidatorIndex;

public class EpochTransitionSummary {

  public class EpochSummary {
    Gwei validatorBalance;
    Gwei boundaryAttestingBalance;

    List<ValidatorIndex> activeAttesters = new ArrayList<>();
    List<ValidatorIndex> boundaryAttesters = new ArrayList<>();

    public Gwei getValidatorBalance() {
      return validatorBalance;
    }

    public Gwei getBoundaryAttestingBalance() {
      return boundaryAttestingBalance;
    }

    public List<ValidatorIndex> getActiveAttesters() {
      return activeAttesters;
    }

    public List<ValidatorIndex> getBoundaryAttesters() {
      return boundaryAttesters;
    }
  }

  BeaconStateEx preState;
  BeaconStateEx postState;

  EpochSummary previousEpochSummary = new EpochSummary();
  EpochSummary currentEpochSummary = new EpochSummary();

  Gwei headAttestingBalance;
  Gwei justifiedAttestingBalance;
  List<ValidatorIndex> headAttesters = new ArrayList<>();
  List<ValidatorIndex> justifiedAttesters = new ArrayList<>();

  boolean noFinality;
  Gwei[][] attestationDeltas = {new Gwei[0], new Gwei[0]};
  Gwei[][] crosslinkDeltas = {new Gwei[0], new Gwei[0]};

  List<ValidatorIndex> ejectedValidators = new ArrayList<>();

  public BeaconStateEx getPreState() {
    return preState;
  }

  public BeaconStateEx getPostState() {
    return postState;
  }

  public EpochSummary getPreviousEpochSummary() {
    return previousEpochSummary;
  }

  public EpochSummary getCurrentEpochSummary() {
    return currentEpochSummary;
  }

  public Gwei getHeadAttestingBalance() {
    return headAttestingBalance;
  }

  public Gwei getJustifiedAttestingBalance() {
    return justifiedAttestingBalance;
  }

  public List<ValidatorIndex> getHeadAttesters() {
    return headAttesters;
  }

  public List<ValidatorIndex> getJustifiedAttesters() {
    return justifiedAttesters;
  }

  public boolean isNoFinality() {
    return noFinality;
  }

  public Gwei[][] getAttestationDeltas() {
    return attestationDeltas;
  }

  public Gwei[][] getCrosslinkDeltas() {
    return crosslinkDeltas;
  }

  public List<ValidatorIndex> getEjectedValidators() {
    return ejectedValidators;
  }
}
