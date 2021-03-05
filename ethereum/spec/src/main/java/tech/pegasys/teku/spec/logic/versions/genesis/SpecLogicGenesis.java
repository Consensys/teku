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

package tech.pegasys.teku.spec.logic.versions.genesis;

import tech.pegasys.teku.spec.constants.SpecConstants;
import tech.pegasys.teku.spec.logic.SpecLogic;
import tech.pegasys.teku.spec.logic.common.statetransition.StateTransition;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.EpochProcessor;
import tech.pegasys.teku.spec.logic.common.util.AttestationUtil;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.common.util.BlockProcessorUtil;
import tech.pegasys.teku.spec.logic.common.util.BlockProposalUtil;
import tech.pegasys.teku.spec.logic.common.util.CommitteeUtil;
import tech.pegasys.teku.spec.logic.common.util.ForkChoiceUtil;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;

public class SpecLogicGenesis implements SpecLogic {
  private final CommitteeUtil committeeUtil;
  private final ValidatorsUtil validatorsUtil;
  private final AttestationUtil attestationUtil;
  private final BeaconStateUtil beaconStateUtil;
  private final EpochProcessor epochProcessor;
  private final BlockProcessorUtil blockProcessorUtil;
  private final StateTransition stateTransition;
  private final ForkChoiceUtil forkChoiceUtil;
  private final BlockProposalUtil blockProposalUtil;

  public SpecLogicGenesis(
      final SpecConstants constants, final SchemaDefinitions schemaDefinitions) {
    this.committeeUtil = new CommitteeUtil(constants);
    this.validatorsUtil = new ValidatorsUtil(constants);
    this.beaconStateUtil =
        new BeaconStateUtil(constants, schemaDefinitions, validatorsUtil, this.committeeUtil);
    this.attestationUtil = new AttestationUtil(constants, beaconStateUtil, validatorsUtil);
    this.epochProcessor = new EpochProcessor(constants, validatorsUtil, this.beaconStateUtil);
    this.blockProcessorUtil =
        new BlockProcessorUtil(constants, beaconStateUtil, attestationUtil, validatorsUtil);
    this.stateTransition =
        StateTransition.create(
            constants, blockProcessorUtil, epochProcessor, beaconStateUtil, validatorsUtil);
    this.forkChoiceUtil =
        new ForkChoiceUtil(constants, beaconStateUtil, attestationUtil, stateTransition);
    this.blockProposalUtil = new BlockProposalUtil(stateTransition);
  }

  @Override
  public CommitteeUtil getCommitteeUtil() {
    return committeeUtil;
  }

  @Override
  public ValidatorsUtil getValidatorsUtil() {
    return validatorsUtil;
  }

  @Override
  public BeaconStateUtil getBeaconStateUtil() {
    return beaconStateUtil;
  }

  @Override
  public AttestationUtil getAttestationUtil() {
    return attestationUtil;
  }

  @Override
  public EpochProcessor getEpochProcessor() {
    return epochProcessor;
  }

  @Override
  public BlockProcessorUtil getBlockProcessorUtil() {
    return blockProcessorUtil;
  }

  @Override
  public StateTransition getStateTransition() {
    return stateTransition;
  }

  @Override
  public ForkChoiceUtil getForkChoiceUtil() {
    return forkChoiceUtil;
  }

  @Override
  public BlockProposalUtil getBlockProposalUtil() {
    return blockProposalUtil;
  }
}
