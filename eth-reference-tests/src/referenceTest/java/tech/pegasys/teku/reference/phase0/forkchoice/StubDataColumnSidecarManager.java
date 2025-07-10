/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.reference.phase0.forkchoice;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.AvailabilityChecker;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.AvailabilityCheckerFactory;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.DataAndValidationResult;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;
import tech.pegasys.teku.statetransition.validation.DataColumnSidecarGossipValidator;
import tech.pegasys.teku.statetransition.validation.GossipValidationHelper;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.storage.client.RecentChainData;

public class StubDataColumnSidecarManager implements AvailabilityCheckerFactory<UInt64> {
  private final Spec spec;
  private final RecentChainData recentChainData;
  private final KZG kzg;
  private DataColumnSidecarGossipValidator validator;
  private final Map<UInt64, List<DataColumnSidecar>> dataColumnSidecarBySlot =
      new ConcurrentHashMap<>();
  private static final Logger LOG = LogManager.getLogger();

  public void prepareDataColumnSidecarForBlock(
      final SignedBeaconBlock block, final List<DataColumnSidecar> dataColumnSidecar) {
    dataColumnSidecarBySlot.put(block.getSlot(), dataColumnSidecar);
  }

  public StubDataColumnSidecarManager(
      final Spec spec, final RecentChainData recentChainData, final KZG kzg) {
    this.spec = spec;
    this.recentChainData = recentChainData;
    this.kzg = kzg;
  }

  @Override
  public AvailabilityChecker<UInt64> createAvailabilityChecker(final SignedBeaconBlock block) {
    return new AvailabilityChecker<UInt64>() {

      @Override
      public boolean initiateDataAvailabilityCheck() {
        final MiscHelpersFulu helpers =
            spec.forMilestone(SpecMilestone.FULU).miscHelpers().toVersionFulu().orElseThrow();
        validator =
            DataColumnSidecarGossipValidator.create(
                spec,
                new ConcurrentHashMap<>(),
                new GossipValidationHelper(spec, recentChainData),
                helpers,
                kzg,
                new StubMetricsSystem(),
                recentChainData.getStore());
        return true;
      }

      @Override
      public SafeFuture<DataAndValidationResult<UInt64>> getAvailabilityCheckResult() {
        final UInt64 blockSlot = block.getSlot();
        final BeaconBlockBody blockBody = block.getMessage().getBody();
        final List<DataColumnSidecar> dataColumnSidecars =
            dataColumnSidecarBySlot.remove(blockSlot);

        final Optional<SszList<SszKZGCommitment>> optionalKzgCommitments =
            blockBody.getOptionalBlobKzgCommitments();
        final boolean hasKzgCommitments =
            optionalKzgCommitments.isPresent() && !optionalKzgCommitments.get().isEmpty();
        final boolean hasNoSidecars = dataColumnSidecars == null || dataColumnSidecars.isEmpty();

        if (hasKzgCommitments && hasNoSidecars) {
          LOG.warn(
              "Block at slot {} had {} KZG commitments but no sidecar columns were found",
              blockSlot,
              optionalKzgCommitments.get().size());
          return SafeFuture.completedFuture(
              DataAndValidationResult.invalidResult(Collections.emptyList()));
        }

        if (hasNoSidecars) {
          // No sidecars and no KZG commitments, treat as valid
          return SafeFuture.completedFuture(
              DataAndValidationResult.validResult(Collections.emptyList()));
        }

        return SafeFuture.collectAll(dataColumnSidecars.stream().map(validator::validate))
            .thenApply(
                validationResults -> {
                  boolean anyRejected =
                      validationResults.stream().anyMatch(InternalValidationResult::isReject);
                  if (anyRejected) {
                    validationResults.stream()
                        .filter(InternalValidationResult::isReject)
                        .forEach(
                            result ->
                                LOG.warn(
                                    "Data column sidecar validation failed: {}",
                                    result.getDescription()));
                    return DataAndValidationResult.invalidResult(Collections.emptyList());
                  }
                  return DataAndValidationResult.validResult(Collections.emptyList());
                });
      }
    };
  }
}
