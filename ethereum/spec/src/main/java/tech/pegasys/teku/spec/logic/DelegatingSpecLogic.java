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

package tech.pegasys.teku.spec.logic;

import tech.pegasys.teku.spec.logic.common.statetransition.StateTransition;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.EpochProcessor;
import tech.pegasys.teku.spec.logic.common.util.AttestationUtil;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.common.util.BlockProcessorUtil;
import tech.pegasys.teku.spec.logic.common.util.BlockProposalUtil;
import tech.pegasys.teku.spec.logic.common.util.CommitteeUtil;
import tech.pegasys.teku.spec.logic.common.util.ForkChoiceUtil;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;

public class DelegatingSpecLogic implements SpecLogic {
  private final SpecLogic specLogic;

  public DelegatingSpecLogic(final SpecLogic specLogic) {
    this.specLogic = specLogic;
  }

  @Override
  public CommitteeUtil getCommitteeUtil() {
    return specLogic.getCommitteeUtil();
  }

  @Override
  public ValidatorsUtil getValidatorsUtil() {
    return specLogic.getValidatorsUtil();
  }

  @Override
  public BeaconStateUtil getBeaconStateUtil() {
    return specLogic.getBeaconStateUtil();
  }

  @Override
  public AttestationUtil getAttestationUtil() {
    return specLogic.getAttestationUtil();
  }

  @Override
  public EpochProcessor getEpochProcessor() {
    return specLogic.getEpochProcessor();
  }

  @Override
  public BlockProcessorUtil getBlockProcessorUtil() {
    return specLogic.getBlockProcessorUtil();
  }

  @Override
  public StateTransition getStateTransition() {
    return specLogic.getStateTransition();
  }

  @Override
  public ForkChoiceUtil getForkChoiceUtil() {
    return specLogic.getForkChoiceUtil();
  }

  @Override
  public BlockProposalUtil getBlockProposalUtil() {
    return specLogic.getBlockProposalUtil();
  }
}
