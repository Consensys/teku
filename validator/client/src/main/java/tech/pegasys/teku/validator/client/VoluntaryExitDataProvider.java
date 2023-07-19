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

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.exceptions.BadRequestException;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.operations.VoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.beaconnode.GenesisDataProvider;

public class VoluntaryExitDataProvider {
  private final Spec spec;
  private final KeyManager keyManager;
  private final ValidatorApiChannel validatorApiChannel;
  private final GenesisDataProvider genesisDataProvider;
  private final TimeProvider timeProvider;

  public VoluntaryExitDataProvider(
      final Spec spec,
      final KeyManager keyManager,
      final ValidatorApiChannel validatorApiChannel,
      final GenesisDataProvider genesisDataProvider,
      final TimeProvider timeProvider) {
    this.spec = spec;
    this.keyManager = keyManager;
    this.validatorApiChannel = validatorApiChannel;
    this.genesisDataProvider = genesisDataProvider;
    this.timeProvider = timeProvider;
  }

  public SafeFuture<SignedVoluntaryExit> createSignedVoluntaryExit(
      final BLSPublicKey publicKey, final Optional<UInt64> maybeEpoch) {
    return validatorApiChannel
        .getGenesisData()
        .thenCompose(
            genesisData -> {
              if (genesisData.isEmpty()) {
                throw new InvalidConfigurationException(
                    "Unable to fetch genesis data, cannot generate an exit.");
              }

              return calculateCurrentEpoch(maybeEpoch)
                  .thenCombine(
                      validatorApiChannel.getValidatorIndices(Set.of(publicKey)),
                      (epoch, indicesMap) -> {
                        final Bytes32 genesisRoot = genesisData.get().getGenesisValidatorsRoot();
                        final Fork fork = spec.getForkSchedule().getFork(epoch);
                        final ForkInfo forkInfo = new ForkInfo(fork, genesisRoot);
                        return calculateSignedVoluntaryExit(indicesMap, publicKey, epoch, forkInfo);
                      });
            });
  }

  private SafeFuture<UInt64> calculateCurrentEpoch(final Optional<UInt64> maybeEpoch) {
    return maybeEpoch
        .map(SafeFuture::completedFuture)
        .orElseGet(
            () ->
                genesisDataProvider
                    .getGenesisTime()
                    .thenApply(
                        genesisTime -> {
                          final SpecVersion genesisSpec = spec.getGenesisSpec();
                          final UInt64 currentTime = timeProvider.getTimeInSeconds();
                          final UInt64 slot =
                              genesisSpec.miscHelpers().computeSlotAtTime(genesisTime, currentTime);
                          return spec.computeEpochAtSlot(slot);
                        }));
  }

  private SignedVoluntaryExit calculateSignedVoluntaryExit(
      final Map<BLSPublicKey, Integer> indicesMap,
      final BLSPublicKey publicKey,
      final UInt64 epoch,
      final ForkInfo forkInfo) {
    final int validatorIndex = indicesMap.get(publicKey);
    final Validator validator =
        keyManager.getActiveValidatorKeys().stream()
            .filter(v -> v.getPublicKey().equals(publicKey))
            .findFirst()
            .orElseThrow(
                () -> new BadRequestException("Validator not found with public key value."));
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
