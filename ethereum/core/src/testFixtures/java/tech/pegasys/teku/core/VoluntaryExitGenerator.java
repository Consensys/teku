/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.core;

import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;

import com.google.common.primitives.UnsignedLong;
import java.util.List;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.core.signatures.LocalMessageSignerService;
import tech.pegasys.teku.core.signatures.Signer;
import tech.pegasys.teku.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.datastructures.operations.VoluntaryExit;
import tech.pegasys.teku.datastructures.state.BeaconState;

public class VoluntaryExitGenerator {
  private final List<BLSKeyPair> validatorKeys;

  public VoluntaryExitGenerator(final List<BLSKeyPair> validatorKeys) {
    this.validatorKeys = validatorKeys;
  }

  public SignedVoluntaryExit generateVoluntaryExit(BeaconState state, int validatorIndex) {
    VoluntaryExit exit =
        new VoluntaryExit(
            compute_epoch_at_slot(state.getSlot()), UnsignedLong.valueOf(validatorIndex));

    BLSSignature exitSignature =
        new Signer(new LocalMessageSignerService(validatorKeys.get(validatorIndex)))
            .signVoluntaryExit(exit, state.getForkInfo())
            .join();

    return new SignedVoluntaryExit(exit, exitSignature);
  }
}
