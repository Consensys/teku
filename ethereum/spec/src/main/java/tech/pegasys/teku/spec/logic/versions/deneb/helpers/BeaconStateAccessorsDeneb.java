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

package tech.pegasys.teku.spec.logic.versions.deneb.helpers;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.config.SpecConfigCapella;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.BeaconStateAccessorsAltair;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.MiscHelpersAltair;

public class BeaconStateAccessorsDeneb extends BeaconStateAccessorsAltair {

  public BeaconStateAccessorsDeneb(
      SpecConfigAltair config, Predicates predicates, MiscHelpersAltair miscHelpers) {
    super(config, predicates, miscHelpers);
  }

  @Override
  public Bytes32 getVoluntaryExitDomain(
      final SignedVoluntaryExit signedVoluntaryExit, final BeaconState state) {
    return miscHelpers.computeDomain(
        Domain.VOLUNTARY_EXIT,
        SpecConfigCapella.required(config).getCapellaForkVersion(),
        state.getGenesisValidatorsRoot());
  }
}
