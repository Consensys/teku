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

package org.ethereum.beacon.consensus.verifier.operation;

import org.ethereum.beacon.consensus.BeaconChainSpec;
import org.ethereum.beacon.consensus.spec.SpecCommons;
import org.ethereum.beacon.consensus.verifier.OperationVerifier;
import org.ethereum.beacon.consensus.verifier.VerificationResult;
import org.ethereum.beacon.core.BeaconState;
import org.ethereum.beacon.core.operations.VoluntaryExit;

/**
 * Verifies {@link VoluntaryExit} beacon chain operation.
 *
 * @see VoluntaryExit
 * @see <a
 *     href="https://github.com/ethereum/eth2.0-specs/blob/v0.8.1/specs/core/0_beacon-chain.md#voluntary-exits">Voluntary
 *     exits</a> in the spec.
 */
public class VoluntaryExitVerifier implements OperationVerifier<VoluntaryExit> {

  private BeaconChainSpec spec;

  public VoluntaryExitVerifier(BeaconChainSpec spec) {
    this.spec = spec;
  }

  @Override
  public VerificationResult verify(VoluntaryExit voluntaryExit, BeaconState state) {
    try {
      spec.verify_voluntary_exit(state, voluntaryExit);
      return VerificationResult.PASSED;
    } catch (SpecCommons.SpecAssertionFailed e) {
      return VerificationResult.failedResult(e.getMessage());
    }
  }
}
