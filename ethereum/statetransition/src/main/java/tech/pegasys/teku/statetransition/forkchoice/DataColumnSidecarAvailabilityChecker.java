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

package tech.pegasys.teku.statetransition.forkchoice;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.AvailabilityChecker;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.DataAndValidationResult;
import tech.pegasys.teku.statetransition.datacolumns.DataAvailabilitySampler;

public class DataColumnSidecarAvailabilityChecker implements AvailabilityChecker<UInt64> {
  private static final Logger LOG = LogManager.getLogger();

  private final DataAvailabilitySampler dataAvailabilitySampler;
  private final SafeFuture<DataAndValidationResult<UInt64>> validationResult = new SafeFuture<>();
  final Spec spec;

  private final SignedBeaconBlock block;

  public DataColumnSidecarAvailabilityChecker(
      final DataAvailabilitySampler dataAvailabilitySampler,
      final Spec spec,
      final SignedBeaconBlock block) {
    this.dataAvailabilitySampler = dataAvailabilitySampler;
    this.spec = spec;
    this.block = block;
  }

  @Override
  public boolean initiateDataAvailabilityCheck() {
    LOG.debug("Starting data availability check for slot {}", block.getSlot());
    switch (dataAvailabilitySampler.checkSamplingEligibility(block.getMessage())) {
      case NOT_REQUIRED_BEFORE_FULU -> {
        validationResult.complete(DataAndValidationResult.notRequired());
        LOG.debug("Availability check for slot {} NOT_REQUIRED, Fulu not started", block.getSlot());
      }
      case NOT_REQUIRED_OLD_EPOCH -> {
        validationResult.complete(DataAndValidationResult.notRequired());
        LOG.debug("Availability check for slot {} NOT_REQUIRED, epoch too old ", block.getSlot());
      }
      case NOT_REQUIRED_NO_BLOBS -> {
        validationResult.complete(DataAndValidationResult.notRequired());
        LOG.debug(
            "Availability check for slot {} NOT_REQUIRED, kzg commitments empty", block.getSlot());
      }
      default -> {
        dataAvailabilitySampler
            .checkDataAvailability(block)
            .finish(
                sampleIndices -> {
                  validationResult.complete(DataAndValidationResult.validResult(sampleIndices));
                },
                throwable ->
                    validationResult.complete(DataAndValidationResult.notAvailable(throwable)));
        dataAvailabilitySampler.flush();
      }
    }
    return true;
  }

  @Override
  public SafeFuture<DataAndValidationResult<UInt64>> getAvailabilityCheckResult() {
    return validationResult;
  }
}
