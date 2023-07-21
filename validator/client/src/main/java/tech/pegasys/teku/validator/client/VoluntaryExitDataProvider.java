/*
 * Copyright ConsenSys Software Inc., 2023
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

package tech.pegasys.teku.validator.client;

import java.util.Optional;
import tech.pegasys.teku.api.exceptions.BadRequestException;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.operations.VoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;

public class VoluntaryExitDataProvider {
  private final Spec spec;
  private final KeyManager keyManager;
  private final TimeProvider timeProvider;

  VoluntaryExitDataProvider(
      final Spec spec, final KeyManager keyManager, final TimeProvider timeProvider) {
    this.spec = spec;
    this.keyManager = keyManager;
    this.timeProvider = timeProvider;
  }

  UInt64 calculateCurrentEpoch(final UInt64 genesisTime) {
    final SpecVersion genesisSpec = spec.getGenesisSpec();
    final UInt64 currentTime = timeProvider.getTimeInSeconds();
    final UInt64 slot = genesisSpec.miscHelpers().computeSlotAtTime(genesisTime, currentTime);
    return spec.computeEpochAtSlot(slot);
  }

  SignedVoluntaryExit createSignedVoluntaryExit(
      final int validatorIndex,
      final BLSPublicKey publicKey,
      final UInt64 epoch,
      final ForkInfo forkInfo) {
    final Validator validator =
        keyManager.getActiveValidatorKeys().stream()
            .filter(v -> v.getPublicKey().equals(publicKey))
            .findFirst()
            .orElseThrow(
                () ->
                    new BadRequestException(
                        String.format(
                            "Validator %s is not in the list of keys managed by this service.",
                            publicKey)));
    final VoluntaryExit message = new VoluntaryExit(epoch, UInt64.valueOf(validatorIndex));
    final BLSSignature signature =
        Optional.ofNullable(validator)
            .orElseThrow()
            .getSigner()
            .signVoluntaryExit(message, forkInfo)
            .join();

    return new SignedVoluntaryExit(message, signature);
  }
}
