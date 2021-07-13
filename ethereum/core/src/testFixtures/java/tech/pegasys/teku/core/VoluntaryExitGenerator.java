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

import static tech.pegasys.teku.infrastructure.async.SyncAsyncRunner.SYNC_RUNNER;
import static tech.pegasys.teku.spec.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;

import java.util.List;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.BLSTestUtil;
import tech.pegasys.teku.core.signatures.LocalSigner;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.operations.VoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class VoluntaryExitGenerator {

  private final Spec spec;
  private final List<BLSKeyPair> validatorKeys;

  public VoluntaryExitGenerator(final Spec spec, final List<BLSKeyPair> validatorKeys) {
    this.spec = spec;
    this.validatorKeys = validatorKeys;
  }

  private SignedVoluntaryExit create(
      ForkInfo forkInfo, UInt64 epoch, int validatorIndex, boolean valid) {
    VoluntaryExit exit = new VoluntaryExit(epoch, UInt64.valueOf(validatorIndex));

    BLSSignature exitSignature =
        new LocalSigner(spec, getKeypair(validatorIndex, valid), SYNC_RUNNER)
            .signVoluntaryExit(exit, forkInfo)
            .join();

    return new SignedVoluntaryExit(exit, exitSignature);
  }

  public SignedVoluntaryExit withInvalidSignature(BeaconState state, int validatorIndex) {
    return create(
        state.getForkInfo(), compute_epoch_at_slot(state.getSlot()), validatorIndex, false);
  }

  public SignedVoluntaryExit valid(
      BeaconState state, int validatorIndex, boolean checkForHavingBeenActiveLongEnough) {
    if (checkForHavingBeenActiveLongEnough) {
      checkForValidatorHavingBeenActiveLongEnough(state, validatorIndex);
    }
    return create(
        state.getForkInfo(), compute_epoch_at_slot(state.getSlot()), validatorIndex, true);
  }

  public SignedVoluntaryExit valid(BeaconState state, int validatorIndex) {
    return valid(state, validatorIndex, true);
  }

  public SignedVoluntaryExit withEpoch(BeaconState state, int epoch, int validatorIndex) {
    return create(state.getForkInfo(), UInt64.valueOf(epoch), validatorIndex, true);
  }

  private BLSKeyPair getKeypair(int validatorIndex, boolean valid) {
    return valid ? validatorKeys.get(validatorIndex) : BLSTestUtil.randomKeyPair(12345);
  }

  // It is easy to miss to update the state to a slot where validator can finally exit. This check
  // is to
  // ensure that the passed state slot is high enough to make sure that doesn't happen.
  private void checkForValidatorHavingBeenActiveLongEnough(BeaconState state, int validatorIndex) {
    if (state
            .getValidators()
            .get(validatorIndex)
            .getActivation_epoch()
            .plus(spec.getSpecConfig(spec.getCurrentEpoch(state)).getShardCommitteePeriod())
            .compareTo(compute_epoch_at_slot(state.getSlot()))
        >= 0) {
      throw new IllegalStateException(
          "Validator has not been active long enough to have a valid exit");
    }
  }
}
