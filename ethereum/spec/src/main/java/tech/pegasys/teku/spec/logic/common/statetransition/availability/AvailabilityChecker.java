/*
 * Copyright Consensys Software Inc., 2023
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

package tech.pegasys.teku.spec.logic.common.statetransition.availability;

import static tech.pegasys.teku.spec.logic.common.statetransition.availability.DataAndValidationResult.notRequired;
import static tech.pegasys.teku.spec.logic.common.statetransition.availability.DataAndValidationResult.notRequiredResultFuture;

import java.util.List;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.NewPayloadRequest;
import tech.pegasys.teku.spec.logic.versions.bellatrix.block.OptimisticExecutionPayloadExecutor;

public interface AvailabilityChecker<Data> {

  AvailabilityChecker<BlobSidecar> NOOP_BLOBSIDECAR =
      new AvailabilityChecker<>() {
        @Override
        public boolean initiateDataAvailabilityCheck() {
          return true;
        }

        @Override
        public SafeFuture<DataAndValidationResult<BlobSidecar>> getAvailabilityCheckResult() {
          return notRequiredResultFuture();
        }

        @Override
        public DataAndValidationResult<BlobSidecar> validateImmediately(
            final List<BlobSidecar> dataList) {
          return notRequired();
        }
      };

  AvailabilityChecker<DataColumnSidecar> NOOP_DATACOLUMN_SIDECAR =
      new AvailabilityChecker<>() {
        @Override
        public boolean initiateDataAvailabilityCheck() {
          return true;
        }

        @Override
        public SafeFuture<DataAndValidationResult<DataColumnSidecar>> getAvailabilityCheckResult() {
          return notRequiredResultFuture();
        }

        @Override
        public DataAndValidationResult<DataColumnSidecar> validateImmediately(
            final List<DataColumnSidecar> dataList) {
          return notRequired();
        }
      };

  /**
   * Similar to {@link OptimisticExecutionPayloadExecutor#optimisticallyExecute(
   * ExecutionPayloadHeader, NewPayloadRequest)}
   *
   * @return true if data availability check is initiated or false to immediately fail the
   *     validation
   */
  boolean initiateDataAvailabilityCheck();

  SafeFuture<DataAndValidationResult<Data>> getAvailabilityCheckResult();

  /** Perform the data availability check immediately on the provided blob sidecars */
  DataAndValidationResult<Data> validateImmediately(List<Data> dataList);
}
