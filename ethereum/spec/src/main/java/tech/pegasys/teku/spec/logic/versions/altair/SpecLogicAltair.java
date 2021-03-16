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

package tech.pegasys.teku.spec.logic.versions.altair;

import tech.pegasys.teku.spec.constants.SpecConstants;
import tech.pegasys.teku.spec.logic.common.AbstractSpecLogic;
import tech.pegasys.teku.spec.logic.common.statetransition.StateTransition;
import tech.pegasys.teku.spec.logic.common.util.AttestationUtil;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.common.util.BlockProposalUtil;
import tech.pegasys.teku.spec.logic.common.util.CommitteeUtil;
import tech.pegasys.teku.spec.logic.common.util.ForkChoiceUtil;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;
import tech.pegasys.teku.spec.logic.versions.altair.statetransition.epoch.EpochProcessorAltair;
import tech.pegasys.teku.spec.logic.versions.altair.statetransition.epoch.ValidatorStatusFactoryAltair;
import tech.pegasys.teku.spec.logic.versions.altair.util.BlockProcessorAltair;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;

public class SpecLogicAltair extends AbstractSpecLogic {
  private SpecLogicAltair(
      final CommitteeUtil committeeUtil,
      final ValidatorsUtil validatorsUtil,
      final BeaconStateUtil beaconStateUtil,
      final AttestationUtil attestationUtil,
      final ValidatorStatusFactoryAltair validatorStatusFactory,
      final EpochProcessorAltair epochProcessor,
      final BlockProcessorAltair blockProcessorUtil,
      final StateTransition stateTransition,
      final ForkChoiceUtil forkChoiceUtil,
      final BlockProposalUtil blockProposalUtil) {
    super(
        committeeUtil,
        validatorsUtil,
        beaconStateUtil,
        attestationUtil,
        validatorStatusFactory,
        epochProcessor,
        blockProcessorUtil,
        stateTransition,
        forkChoiceUtil,
        blockProposalUtil);
  }

  public static SpecLogicAltair create(
      final SpecConstants constants, final SchemaDefinitions schemaDefinitions) {
    final CommitteeUtil committeeUtil = new CommitteeUtil(constants);
    final ValidatorsUtil validatorsUtil = new ValidatorsUtil(constants);
    final BeaconStateUtil beaconStateUtil =
        new BeaconStateUtil(constants, schemaDefinitions, validatorsUtil, committeeUtil);
    final AttestationUtil attestationUtil =
        new AttestationUtil(constants, beaconStateUtil, validatorsUtil);
    final ValidatorStatusFactoryAltair validatorStatusFactory =
        new ValidatorStatusFactoryAltair(beaconStateUtil, attestationUtil, validatorsUtil);
    final EpochProcessorAltair epochProcessor =
        new EpochProcessorAltair(
            constants, validatorsUtil, beaconStateUtil, validatorStatusFactory);
    final BlockProcessorAltair blockProcessorUtil =
        new BlockProcessorAltair(constants, beaconStateUtil, attestationUtil, validatorsUtil);
    final StateTransition stateTransition =
        StateTransition.create(
            constants, blockProcessorUtil, epochProcessor, beaconStateUtil, validatorsUtil);
    final ForkChoiceUtil forkChoiceUtil =
        new ForkChoiceUtil(constants, beaconStateUtil, attestationUtil, stateTransition);
    final BlockProposalUtil blockProposalUtil =
        new BlockProposalUtil(schemaDefinitions, stateTransition);

    return new SpecLogicAltair(
        committeeUtil,
        validatorsUtil,
        beaconStateUtil,
        attestationUtil,
        validatorStatusFactory,
        epochProcessor,
        blockProcessorUtil,
        stateTransition,
        forkChoiceUtil,
        blockProposalUtil);
  }
}
