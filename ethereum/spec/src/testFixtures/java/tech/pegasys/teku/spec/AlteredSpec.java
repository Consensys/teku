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

package tech.pegasys.teku.spec;

import tech.pegasys.teku.spec.constants.SpecConstants;
import tech.pegasys.teku.spec.util.BeaconStateUtil;
import tech.pegasys.teku.spec.util.CommitteeUtil;

public class AlteredSpec extends Spec {

  public static Spec createAlteredSpec(
      final SpecConstants specConstants, final boolean verifyDeposits) {
    final CommitteeUtil committeeUtil = new CommitteeUtil(specConstants);
    final AlteredBeaconStateUtil alteredBeaconStateUtil =
        new AlteredBeaconStateUtil(specConstants, committeeUtil, verifyDeposits);
    return new Spec(specConstants, committeeUtil, alteredBeaconStateUtil);
  }

  AlteredSpec(
      final SpecConstants specConstants,
      final CommitteeUtil committeeUtil,
      final BeaconStateUtil beaconStateUtil) {
    super(specConstants, committeeUtil, beaconStateUtil);
  }
}
