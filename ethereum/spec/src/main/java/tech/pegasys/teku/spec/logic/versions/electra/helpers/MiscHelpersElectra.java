/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.spec.logic.versions.electra.helpers;

import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateElectra;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.logic.versions.deneb.helpers.MiscHelpersDeneb;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;

public class MiscHelpersElectra extends MiscHelpersDeneb {

  public MiscHelpersElectra(
      final SpecConfigElectra specConfig,
      final Predicates predicates,
      final SchemaDefinitionsElectra schemaDefinitions) {
    super(specConfig, predicates, schemaDefinitions);
  }

  @Override
  public boolean isFormerDepositMechanismDisabled(final BeaconState state) {
    return state
        .getEth1DepositIndex()
        .equals(BeaconStateElectra.required(state).getDepositReceiptsStartIndex());
  }
}
