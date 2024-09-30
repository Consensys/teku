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

import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.AvailabilityChecker;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.DataAndValidationResult;
import tech.pegasys.teku.statetransition.datacolumns.DataAvailabilitySampler;

public class DataColumnSidecarAvailabilityChecker
    implements AvailabilityChecker<DataColumnSidecar> {
  private static final Logger LOG = LogManager.getLogger("das-nyota");

  private final DataAvailabilitySampler dataAvailabilitySampler;
  private final SafeFuture<DataAndValidationResult<DataColumnSidecar>> validationResult =
      new SafeFuture<>();
  final KZG kzg;
  final Spec spec;

  private final SignedBeaconBlock block;

  public DataColumnSidecarAvailabilityChecker(
      final DataAvailabilitySampler dataAvailabilitySampler,
      final KZG kzg,
      final Spec spec,
      final SignedBeaconBlock block) {
    this.dataAvailabilitySampler = dataAvailabilitySampler;
    this.kzg = kzg;
    this.spec = spec;
    this.block = block;
  }

  @Override
  public boolean initiateDataAvailabilityCheck() {
    LOG.info("Starting data availability check for slot {}", block.getSlot());
    switch (dataAvailabilitySampler.checkSamplingEligibility(block.getMessage())) {
      case NOT_REQUIRED_BEFORE_EIP7594 -> {
        validationResult.complete(DataAndValidationResult.notRequired());
        LOG.info(
            "Availability check for slot {} NOT_REQUIRED, EIP7594 not started", block.getSlot());
      }
      case NOT_REQUIRED_OLD_EPOCH -> {
        validationResult.complete(DataAndValidationResult.notRequired());
        LOG.info("Availability check for slot {} NOT_REQUIRED, epoch too old ", block.getSlot());
      }
      case NOT_REQUIRED_NO_BLOBS -> {
        validationResult.complete(DataAndValidationResult.notRequired());
        LOG.info(
            "Availability check for slot {} NOT_REQUIRED, kzg commitments empty", block.getSlot());
      }
      default -> dataAvailabilitySampler
          .checkDataAvailability(block.getSlot(), block.getRoot(), block.getParentRoot())
          .finish(
              dataColumnSidecars ->
                  validationResult.complete(validateImmediately(dataColumnSidecars)),
              throwable ->
                  validationResult.complete(DataAndValidationResult.notAvailable(throwable)));
    }
    return true;
  }

  @Override
  public SafeFuture<DataAndValidationResult<DataColumnSidecar>> getAvailabilityCheckResult() {
    return validationResult;
  }

  @Override
  public DataAndValidationResult<DataColumnSidecar> validateImmediately(
      List<DataColumnSidecar> dataColumnSidecars) {
    if (dataColumnSidecars.isEmpty()) {
      return DataAndValidationResult.validResult(dataColumnSidecars);
    }
    final boolean isNotValid =
        dataColumnSidecars.stream()
            .parallel()
            .map(
                dataColumnSidecar ->
                    spec.atSlot(dataColumnSidecar.getSlot())
                        .miscHelpers()
                        .toVersionEip7594()
                        .map(
                            miscHelpersEip7594 ->
                                miscHelpersEip7594.verifyDataColumnSidecarKzgProof(
                                    kzg, dataColumnSidecar))
                        .orElse(false))
            .anyMatch(isGood -> !isGood);
    if (isNotValid) {
      return DataAndValidationResult.invalidResult(dataColumnSidecars);
    } else {
      return DataAndValidationResult.validResult(dataColumnSidecars);
    }
  }
}
