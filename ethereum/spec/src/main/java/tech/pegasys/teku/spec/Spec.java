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
import tech.pegasys.teku.spec.statetransition.epoch.EpochProcessor;
import tech.pegasys.teku.spec.util.AttestationUtil;
import tech.pegasys.teku.spec.util.BeaconStateUtil;
import tech.pegasys.teku.spec.util.CommitteeUtil;
import tech.pegasys.teku.spec.util.ValidatorsUtil;

public class Spec {
  private final SpecConstants constants;
  private final CommitteeUtil committeeUtil;
  private final ValidatorsUtil validatorsUtil;
  private final AttestationUtil attestationUtil;
  private final BeaconStateUtil beaconStateUtil;
  private final EpochProcessor epochProcessor;

  Spec(final SpecConstants constants) {
    this.constants = constants;
    this.committeeUtil = new CommitteeUtil(this.constants);
    this.validatorsUtil = new ValidatorsUtil(this.constants);
    this.beaconStateUtil = new BeaconStateUtil(this.constants, validatorsUtil, this.committeeUtil);
    this.attestationUtil = new AttestationUtil(constants, beaconStateUtil, validatorsUtil);
    this.epochProcessor = new EpochProcessor(constants, validatorsUtil, this.beaconStateUtil);
  }

  public SpecConstants getConstants() {
    return constants;
  }

  public CommitteeUtil getCommitteeUtil() {
    return committeeUtil;
  }

  public ValidatorsUtil getValidatorsUtil() {
    return validatorsUtil;
  }

  public BeaconStateUtil getBeaconStateUtil() {
    return beaconStateUtil;
  }

  public AttestationUtil getAttestationUtil() {
    return attestationUtil;
  }

  public EpochProcessor getEpochProcessor() {
    return epochProcessor;
  }
}
