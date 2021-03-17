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

package tech.pegasys.teku.spec.logic.versions.altair.util;

import org.apache.commons.lang3.NotImplementedException;
import tech.pegasys.teku.spec.constants.SpecConstantsAltair;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;
import tech.pegasys.teku.spec.logic.common.util.AbstractBlockProcessor;
import tech.pegasys.teku.spec.logic.common.util.AttestationUtil;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;
import tech.pegasys.teku.ssz.SszList;

public class BlockProcessorAltair extends AbstractBlockProcessor {
  public BlockProcessorAltair(
      final SpecConstantsAltair specConstants,
      final BeaconStateUtil beaconStateUtil,
      final AttestationUtil attestationUtil,
      final ValidatorsUtil validatorsUtil) {
    super(specConstants, beaconStateUtil, attestationUtil, validatorsUtil);
  }

  @Override
  public void processAttestationsNoValidation(
      final MutableBeaconState state, final SszList<Attestation> attestations)
      throws BlockProcessingException {
    throw new NotImplementedException("TODO");
  }
}
