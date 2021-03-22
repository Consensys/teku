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

import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.logic.common.AbstractSpecLogic;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.logic.common.statetransition.StateTransition;
import tech.pegasys.teku.spec.logic.common.util.AttestationUtil;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.common.util.BlockProposalUtil;
import tech.pegasys.teku.spec.logic.common.util.CommitteeUtil;
import tech.pegasys.teku.spec.logic.common.util.ForkChoiceUtil;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.BeaconStateAccessorsAltair;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.MiscHelpersAltair;
import tech.pegasys.teku.spec.logic.versions.altair.statetransition.epoch.EpochProcessorAltair;
import tech.pegasys.teku.spec.logic.versions.altair.statetransition.epoch.ValidatorStatusFactoryAltair;
import tech.pegasys.teku.spec.logic.versions.altair.util.BlockProcessorAltair;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;

public class SpecLogicAltair extends AbstractSpecLogic {
  private SpecLogicAltair(
      final Predicates predicates,
      final MiscHelpersAltair miscHelpers,
      final BeaconStateAccessorsAltair beaconStateAccessors,
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
        predicates,
        miscHelpers,
        beaconStateAccessors,
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
      final SpecConfigAltair config, final SchemaDefinitions schemaDefinitions) {
    // Helpers
    final Predicates predicates = new Predicates();
    final MiscHelpersAltair miscHelpers = new MiscHelpersAltair(config);
    final BeaconStateAccessorsAltair beaconStateAccessors =
        new BeaconStateAccessorsAltair(config, predicates, miscHelpers);

    // Util
    final CommitteeUtil committeeUtil = new CommitteeUtil(config, miscHelpers);
    final ValidatorsUtil validatorsUtil = new ValidatorsUtil();
    final BeaconStateUtil beaconStateUtil =
        new BeaconStateUtil(
            config,
            schemaDefinitions,
            validatorsUtil,
            committeeUtil,
            predicates,
            miscHelpers,
            beaconStateAccessors);
    final AttestationUtil attestationUtil =
        new AttestationUtil(config, beaconStateUtil, beaconStateAccessors, miscHelpers);
    final ValidatorStatusFactoryAltair validatorStatusFactory =
        new ValidatorStatusFactoryAltair(
            beaconStateUtil, attestationUtil, predicates, miscHelpers, beaconStateAccessors);
    final EpochProcessorAltair epochProcessor =
        new EpochProcessorAltair(
            config, validatorsUtil, beaconStateUtil, validatorStatusFactory, beaconStateAccessors);
    final BlockProcessorAltair blockProcessorUtil =
        new BlockProcessorAltair(
            config,
            beaconStateUtil,
            attestationUtil,
            validatorsUtil,
            beaconStateAccessors,
            miscHelpers);
    final StateTransition stateTransition =
        StateTransition.create(
            config, blockProcessorUtil, epochProcessor, beaconStateUtil, beaconStateAccessors);
    final ForkChoiceUtil forkChoiceUtil =
        new ForkChoiceUtil(config, beaconStateUtil, attestationUtil, stateTransition, miscHelpers);
    final BlockProposalUtil blockProposalUtil =
        new BlockProposalUtil(schemaDefinitions, stateTransition);

    return new SpecLogicAltair(
        predicates,
        miscHelpers,
        beaconStateAccessors,
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
