/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.spec.logic.versions.merge.helpers;

import tech.pegasys.teku.spec.config.SpecConfigMerge;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.BeaconStateAccessorsAltair;

public class BeaconStateAccessorsMerge extends BeaconStateAccessorsAltair {
  public BeaconStateAccessorsMerge(
      final SpecConfigMerge config,
      final Predicates predicates,
      final MiscHelpersMerge miscHelpers) {
    super(config, predicates, miscHelpers);
  }
}
