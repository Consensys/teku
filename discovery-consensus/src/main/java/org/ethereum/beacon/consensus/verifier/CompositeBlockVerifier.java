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

package org.ethereum.beacon.consensus.verifier;

import static org.ethereum.beacon.consensus.verifier.VerificationResult.PASSED;

import java.util.ArrayList;
import java.util.List;
import org.ethereum.beacon.core.BeaconBlock;
import org.ethereum.beacon.core.BeaconState;

/**
 * Aggregates a number of block verifiers.
 *
 * <p>Verifiers are triggered according to the order that they were added with. If one of
 * verification has failed it returns immediately with this failed result.
 *
 * @see BeaconBlockVerifier
 */
public class CompositeBlockVerifier implements BeaconBlockVerifier {

  private List<BeaconBlockVerifier> verifiers;

  public CompositeBlockVerifier(List<BeaconBlockVerifier> verifiers) {
    this.verifiers = verifiers;
  }

  @Override
  public VerificationResult verify(BeaconBlock block, BeaconState state) {
    for (BeaconBlockVerifier verifier : verifiers) {
      VerificationResult result = verifier.verify(block, state);
      if (result != PASSED) {
        return result;
      }
    }
    return PASSED;
  }

  public static class Builder {
    private List<BeaconBlockVerifier> verifiers;

    private Builder() {
      this.verifiers = new ArrayList<>();
    }

    public static Builder createNew() {
      return new Builder();
    }

    public Builder with(BeaconBlockVerifier verifier) {
      this.verifiers.add(verifier);
      return this;
    }

    public CompositeBlockVerifier build() {
      assert verifiers.size() > 0;
      return new CompositeBlockVerifier(verifiers);
    }
  }
}
