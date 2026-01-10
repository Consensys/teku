/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.spec.logic.versions.gloas.helpers;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.SszMutableList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.TransitionCaches;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.MutableBeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.Builder;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateMutators;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.BeaconStateMutatorsElectra;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;

public class BeaconStateMutatorsGloas extends BeaconStateMutatorsElectra {

  private static final Logger LOG = LogManager.getLogger();

  private final SpecConfigGloas specConfigGloas;
  private final BeaconStateAccessorsGloas beaconStateAccessorsGloas;

  public static BeaconStateMutatorsGloas required(final BeaconStateMutators beaconStateMutators) {
    checkArgument(
        beaconStateMutators instanceof BeaconStateMutatorsGloas,
        "Expected %s but it was %s",
        BeaconStateMutatorsGloas.class,
        beaconStateMutators.getClass());
    return (BeaconStateMutatorsGloas) beaconStateMutators;
  }

  public BeaconStateMutatorsGloas(
      final SpecConfigGloas specConfig,
      final MiscHelpersGloas miscHelpers,
      final BeaconStateAccessorsGloas beaconStateAccessors,
      final SchemaDefinitionsGloas schemaDefinitions) {
    super(specConfig, miscHelpers, beaconStateAccessors, schemaDefinitions);
    this.specConfigGloas = specConfig;
    this.beaconStateAccessorsGloas = beaconStateAccessors;
  }

  /**
   * initiate_builder_exit
   *
   * <p>Initiate the exit of the builder with index ``index``.
   */
  public void initiateBuilderExit(final MutableBeaconState state, final UInt64 builderIndex) {
    final SszMutableList<Builder> builders = MutableBeaconStateGloas.required(state).getBuilders();
    final Builder builder = builders.get(builderIndex.intValue());
    // Return if builder already initiated exit
    if (!builder.getWithdrawableEpoch().equals(FAR_FUTURE_EPOCH)) {
      return;
    }
    // Set builder exit epoch
    final UInt64 exitEpoch =
        beaconStateAccessorsGloas
            .getCurrentEpoch(state)
            .plus(specConfigGloas.getMinBuilderWithdrawabilityDelay());
    builders.set(builderIndex.intValue(), builder.copyWithNewWithdrawableEpoch(exitEpoch));
  }

  public void addBuilderToRegistry(
      final MutableBeaconState state,
      final BLSPublicKey pubkey,
      final Bytes32 withdrawalCredentials,
      final UInt64 amount) {
    final UInt64 index = beaconStateAccessorsGloas.getIndexForNewBuilder(state);
    final Builder builder =
        beaconStateAccessorsGloas.getBuilderFromDeposit(
            state, pubkey, withdrawalCredentials, amount);
    final SszMutableList<Builder> builders = MutableBeaconStateGloas.required(state).getBuilders();
    if (index.isGreaterThanOrEqualTo(builders.size())) {
      LOG.debug("Adding new builder with index {} to state", builders.size());
      builders.append(builder);
    } else {
      // The index is reassigned to a new builder, so updating the caches
      final TransitionCaches caches = BeaconStateCache.getTransitionCaches(state);
      caches.getBuildersPubKeys().invalidateWithNewValue(index, pubkey);
      caches.getBuilderIndexCache().invalidateWithNewValue(pubkey, index.intValue());
      // Remove the pubkey mapping for the old builder
      caches.getBuilderIndexCache().invalidate(builders.get(index.intValue()).getPublicKey());
      LOG.debug(
          "Adding new builder to an existing index {} (builder has exited) in the state",
          index.intValue());
      builders.set(index.intValue(), builder);
    }
  }

  public void applyDepositForBuilder(
      final MutableBeaconState state,
      final BLSPublicKey pubkey,
      final Bytes32 withdrawalCredentials,
      final UInt64 amount,
      final BLSSignature signature) {
    beaconStateAccessorsGloas
        .getBuilderIndex(state, pubkey)
        .ifPresentOrElse(
            builderIndex -> {
              // Increase balance by deposit amount
              final SszMutableList<Builder> builders =
                  MutableBeaconStateGloas.required(state).getBuilders();
              final Builder builder = builders.get(builderIndex);
              builders.set(
                  builderIndex, builder.copyWithNewBalance(builder.getBalance().plus(amount)));
            },
            () -> {
              // Verify the deposit signature (proof of possession) which is not checked by the
              // deposit contract
              if (miscHelpers.isValidDepositSignature(
                  pubkey, withdrawalCredentials, amount, signature)) {
                addBuilderToRegistry(state, pubkey, withdrawalCredentials, amount);
              }
            });
  }
}
