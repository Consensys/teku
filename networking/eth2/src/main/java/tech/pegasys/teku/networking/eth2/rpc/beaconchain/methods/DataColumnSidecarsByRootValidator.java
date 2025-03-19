/*
 * Copyright Consensys Software Inc., 2022
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

package tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods;

import static tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.DataColumnSidecarsResponseInvalidResponseException.InvalidResponseType;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.MetricsHistogram;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnIdentifier;

public class DataColumnSidecarsByRootValidator extends AbstractDataColumnSidecarValidator {
  private final Set<DataColumnIdentifier> expectedDataColumnIdentifiers;
  private final MetricsHistogram dataColumnSidecarInclusionProofVerificationTimeSeconds;

  public DataColumnSidecarsByRootValidator(
      final Peer peer,
      final Spec spec,
      final KZG kzg,
      final MetricsSystem metricsSystem,
      final TimeProvider timeProvider,
      final List<DataColumnIdentifier> expectedDataColumnIdentifiers) {
    super(peer, spec, kzg);
    this.expectedDataColumnIdentifiers = ConcurrentHashMap.newKeySet();
    this.expectedDataColumnIdentifiers.addAll(expectedDataColumnIdentifiers);
    this.dataColumnSidecarInclusionProofVerificationTimeSeconds =
        new MetricsHistogram(
            metricsSystem,
            timeProvider,
            TekuMetricCategory.BEACON,
            "data_column_sidecar_inclusion_proof_verification_seconds",
            "Time taken to verify data column sidecar inclusion proof",
            new double[] {
              0.001, 0.002, 0.003, 0.004, 0.005, 0.01, 0.015, 0.02, 0.025, 0.03, 0.04, 0.05, 0.1,
              0.5, 1.0
            });
  }

  public void validate(final DataColumnSidecar dataColumnSidecar) {
    final DataColumnIdentifier dataColumnIdentifier =
        DataColumnIdentifier.createFromSidecar(dataColumnSidecar);
    if (!expectedDataColumnIdentifiers.remove(dataColumnIdentifier)) {
      throw new DataColumnSidecarsResponseInvalidResponseException(
          peer, InvalidResponseType.DATA_COLUMN_SIDECAR_UNEXPECTED_IDENTIFIER);
    }

    try (MetricsHistogram.Timer ignored =
        dataColumnSidecarInclusionProofVerificationTimeSeconds.startTimer()) {
      verifyInclusionProof(dataColumnSidecar);
    } catch (final IOException ioException) {
      throw new DataColumnSidecarsResponseInvalidResponseException(
          peer, InvalidResponseType.DATA_COLUMN_SIDECAR_INCLUSION_PROOF_VERIFICATION_FAILED);
    }
    verifyKzgProof(dataColumnSidecar);
  }
}
