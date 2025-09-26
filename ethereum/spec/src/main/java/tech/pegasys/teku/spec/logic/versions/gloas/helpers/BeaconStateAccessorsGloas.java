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

import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.IndexedPayloadAttestation;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestation;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.BeaconStateAccessorsFulu;

public class BeaconStateAccessorsGloas extends BeaconStateAccessorsFulu {

  public static BeaconStateAccessorsGloas required(
      final BeaconStateAccessors beaconStateAccessors) {
    checkArgument(
        beaconStateAccessors instanceof BeaconStateAccessorsGloas,
        "Expected %s but it was %s",
        BeaconStateAccessorsGloas.class,
        beaconStateAccessors.getClass());
    return (BeaconStateAccessorsGloas) beaconStateAccessors;
  }

  public BeaconStateAccessorsGloas(
      final SpecConfigGloas config,
      final PredicatesGloas predicates,
      final MiscHelpersGloas miscHelpers) {
    super(config, predicates, miscHelpers);
  }

  public IndexedPayloadAttestation getIndexedPayloadAttestation(
      final BeaconState state, final UInt64 slot, final PayloadAttestation payloadAttestation) {
    return null;
  }
}
