/*
 * Copyright Consensys Software Inc., 2026
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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;
import static tech.pegasys.teku.spec.SpecMilestone.FULU;
import static tech.pegasys.teku.spec.SpecMilestone.GLOAS;

import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.networking.eth2.peers.DataColumnSidecarSignatureValidator;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecarSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecarFulu;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecarSchemaFulu;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.util.DataColumnIdentifier;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.AvailabilityCheckerFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

@SuppressWarnings("JavaCase")
@TestSpecContext(milestone = {FULU})
public class DataColumnSidecarsByRootValidatorTest {

  private final UInt64 currentForkEpoch = UInt64.valueOf(1);
  private final Eth2Peer peer = mock(Eth2Peer.class);
  private final KZG kzg = mock(KZG.class);
  private Spec spec;
  private DataStructureUtil dataStructureUtil;
  private DataColumnSidecarsByRootValidator validator;
  private UInt64 currentForkFirstSlot;
  private final MetricsSystem metricsSystem = new NoOpMetricsSystem();
  private final TimeProvider timeProvider = StubTimeProvider.withTimeInMillis(0);
  private final DataColumnSidecarSignatureValidator dataColumnSidecarSignatureValidator =
      mock(DataColumnSidecarSignatureValidator.class);
  private final CombinedChainDataClient combinedChainDataClient =
      mock(CombinedChainDataClient.class);

  @BeforeEach
  void setUp(final TestSpecInvocationContextProvider.SpecContext specContext) {
    spec =
        switch (specContext.getSpecMilestone()) {
          case FULU -> TestSpecFactory.createMinimalWithFuluForkEpoch(currentForkEpoch);
          case GLOAS -> TestSpecFactory.createMinimalWithGloasForkEpoch(currentForkEpoch);
          default ->
              throw new IllegalArgumentException(
                  String.format("%s is an unsupported milestone", specContext.getSpecMilestone()));
        };
    currentForkFirstSlot = spec.computeStartSlotAtEpoch(currentForkEpoch);
    dataStructureUtil = new DataStructureUtil(spec);
    spec.reinitializeForTesting(
        AvailabilityCheckerFactory.NOOP_BLOB_SIDECAR,
        AvailabilityCheckerFactory.NOOP_DATACOLUMN_SIDECAR,
        kzg);
    when(dataColumnSidecarSignatureValidator.validateSignature(any()))
        .thenReturn(SafeFuture.completedFuture(true));
    when(kzg.verifyCellProofBatch(any(), any(), any())).thenReturn(true);
  }

  @TestTemplate
  void dataColumnSidecarIsCorrect() {
    final SignedBeaconBlock block1 =
        dataStructureUtil.randomSignedBeaconBlock(currentForkFirstSlot);
    final DataColumnSidecar dataColumnSidecar1_0 =
        dataStructureUtil.randomDataColumnSidecarWithInclusionProof(block1, UInt64.ZERO);
    final DataColumnIdentifier sidecarIdentifier1_0 =
        DataColumnIdentifier.createFromSidecar(dataColumnSidecar1_0);
    validator =
        new DataColumnSidecarsByRootValidator(
            peer,
            spec,
            metricsSystem,
            timeProvider,
            dataColumnSidecarSignatureValidator,
            List.of(sidecarIdentifier1_0),
            combinedChainDataClient);

    assertDoesNotThrow(() -> validator.validate(dataColumnSidecar1_0));
  }

  @TestTemplate
  void dataColumnSidecarIdentifierNotRequested() {
    final SignedBeaconBlock block1 =
        dataStructureUtil.randomSignedBeaconBlock(currentForkFirstSlot);
    final DataColumnSidecar dataColumnSidecar2_0 =
        dataStructureUtil.randomDataColumnSidecarWithInclusionProof(
            dataStructureUtil.randomSignedBeaconBlock(currentForkFirstSlot.increment()),
            UInt64.ZERO);
    final DataColumnIdentifier sidecarIdentifier1_0 =
        new DataColumnIdentifier(block1.getRoot(), UInt64.ZERO);
    validator =
        new DataColumnSidecarsByRootValidator(
            peer,
            spec,
            metricsSystem,
            timeProvider,
            dataColumnSidecarSignatureValidator,
            List.of(sidecarIdentifier1_0),
            combinedChainDataClient);

    assertThatSafeFuture(validator.validate(dataColumnSidecar2_0))
        .isCompletedExceptionallyWith(DataColumnSidecarsResponseInvalidResponseException.class)
        .hasMessageContaining(
            DataColumnSidecarsResponseInvalidResponseException.InvalidResponseType
                .DATA_COLUMN_SIDECAR_UNEXPECTED_IDENTIFIER
                .describe());
  }

  @TestTemplate
  void dataColumnSidecarFailsKzgVerification() {
    when(kzg.verifyCellProofBatch(any(), any(), any())).thenReturn(false);
    final SignedBeaconBlock block1 =
        dataStructureUtil.randomSignedBeaconBlock(currentForkFirstSlot);
    final DataColumnSidecar dataColumnSidecar1_0 =
        dataStructureUtil.randomDataColumnSidecarWithInclusionProof(block1, UInt64.ZERO);
    final DataColumnIdentifier sidecarIdentifier1_0 =
        DataColumnIdentifier.createFromSidecar(dataColumnSidecar1_0);
    validator =
        new DataColumnSidecarsByRootValidator(
            peer,
            spec,
            metricsSystem,
            timeProvider,
            dataColumnSidecarSignatureValidator,
            List.of(sidecarIdentifier1_0),
            combinedChainDataClient);

    assertThatSafeFuture(validator.validate(dataColumnSidecar1_0))
        .isCompletedExceptionallyWith(DataColumnSidecarsResponseInvalidResponseException.class)
        .hasMessageContaining(
            DataColumnSidecarsResponseInvalidResponseException.InvalidResponseType
                .DATA_COLUMN_SIDECAR_KZG_VERIFICATION_FAILED
                .describe());
  }

  @TestTemplate
  void dataColumnSidecarFailsInclusionProofVerification() {
    final SignedBeaconBlock block1 =
        dataStructureUtil.randomSignedBeaconBlock(currentForkFirstSlot);
    final DataColumnSidecar dataColumnSidecar1_0 =
        dataStructureUtil.randomDataColumnSidecarWithInclusionProof(block1, UInt64.ZERO);
    final DataColumnSidecar dataColumnSidecar1_0_modified =
        breakInclusionProof(dataColumnSidecar1_0);
    final DataColumnIdentifier sidecarIdentifier1_0 =
        DataColumnIdentifier.createFromSidecar(dataColumnSidecar1_0);
    validator =
        new DataColumnSidecarsByRootValidator(
            peer,
            spec,
            metricsSystem,
            timeProvider,
            dataColumnSidecarSignatureValidator,
            List.of(sidecarIdentifier1_0),
            combinedChainDataClient);

    assertThatSafeFuture(validator.validate(dataColumnSidecar1_0_modified))
        .isCompletedExceptionallyWith(DataColumnSidecarsResponseInvalidResponseException.class)
        .hasMessageContaining(
            DataColumnSidecarsResponseInvalidResponseException.InvalidResponseType
                .DATA_COLUMN_SIDECAR_INCLUSION_PROOF_VERIFICATION_FAILED
                .describe());
  }

  @TestTemplate
  void dataColumnSidecarResponseWithDuplicateSidecar() {
    final SignedBeaconBlock block1 =
        dataStructureUtil.randomSignedBeaconBlock(currentForkFirstSlot);
    final DataColumnSidecar dataColumnSidecar1_0 =
        dataStructureUtil.randomDataColumnSidecarWithInclusionProof(block1, UInt64.ZERO);
    final DataColumnIdentifier sidecarIdentifier1_0 =
        DataColumnIdentifier.createFromSidecar(dataColumnSidecar1_0);
    validator =
        new DataColumnSidecarsByRootValidator(
            peer,
            spec,
            metricsSystem,
            timeProvider,
            dataColumnSidecarSignatureValidator,
            List.of(sidecarIdentifier1_0),
            combinedChainDataClient);

    assertDoesNotThrow(() -> validator.validate(dataColumnSidecar1_0).join());
    assertThatSafeFuture(validator.validate(dataColumnSidecar1_0))
        .isCompletedExceptionallyWith(DataColumnSidecarsResponseInvalidResponseException.class)
        .hasMessageContaining(
            DataColumnSidecarsResponseInvalidResponseException.InvalidResponseType
                .DATA_COLUMN_SIDECAR_UNEXPECTED_IDENTIFIER
                .describe());
  }

  @TestTemplate
  void dataColumnSidecarFailsSignatureVerification() {
    when(dataColumnSidecarSignatureValidator.validateSignature(any()))
        .thenReturn(SafeFuture.completedFuture(false));
    final SignedBeaconBlock block1 =
        dataStructureUtil.randomSignedBeaconBlock(currentForkFirstSlot);
    final DataColumnSidecar dataColumnSidecar1_0 =
        dataStructureUtil.randomDataColumnSidecarWithInclusionProof(block1, UInt64.ZERO);
    final DataColumnIdentifier sidecarIdentifier1_0 =
        DataColumnIdentifier.createFromSidecar(dataColumnSidecar1_0);
    validator =
        new DataColumnSidecarsByRootValidator(
            peer,
            spec,
            metricsSystem,
            timeProvider,
            dataColumnSidecarSignatureValidator,
            List.of(sidecarIdentifier1_0),
            combinedChainDataClient);

    // not a part of validate, separate check
    assertThat(validator.verifySignature(dataColumnSidecar1_0)).isCompletedWithValue(false);
  }

  @TestTemplate
  void dataColumnSidecarFailsValidityCheck() {
    final SignedBeaconBlock block1 =
        dataStructureUtil.randomSignedBeaconBlock(currentForkFirstSlot);
    final DataColumnSidecar dataColumnSidecar1_0 =
        dataStructureUtil.randomDataColumnSidecarWithInclusionProof(block1, UInt64.ZERO);
    final DataColumnSidecar dataColumnSidecar1_0_modified = breakValidity(dataColumnSidecar1_0);
    final DataColumnIdentifier sidecarIdentifier1_0 =
        DataColumnIdentifier.createFromSidecar(dataColumnSidecar1_0);
    validator =
        new DataColumnSidecarsByRootValidator(
            peer,
            spec,
            metricsSystem,
            timeProvider,
            dataColumnSidecarSignatureValidator,
            List.of(sidecarIdentifier1_0),
            combinedChainDataClient);

    assertThatSafeFuture(validator.validate(dataColumnSidecar1_0_modified))
        .isCompletedExceptionallyWith(DataColumnSidecarsResponseInvalidResponseException.class)
        .hasMessageContaining(
            DataColumnSidecarsResponseInvalidResponseException.InvalidResponseType
                .DATA_COLUMN_SIDECAR_VALIDITY_CHECK_FAILED
                .describe());
  }

  public static DataColumnSidecar breakInclusionProof(final DataColumnSidecar dataColumnSidecar) {
    final DataColumnSidecarSchemaFulu dataColumnSidecarSchemaFulu =
        DataColumnSidecarSchemaFulu.required(
            (DataColumnSidecarSchema<?>) dataColumnSidecar.getSchema());
    return dataColumnSidecarSchemaFulu.create(
        builder ->
            builder
                .index(dataColumnSidecar.getIndex())
                .column(dataColumnSidecar.getColumn())
                .kzgCommitments(dataColumnSidecar.getKzgCommitments())
                .kzgProofs(dataColumnSidecar.getKzgProofs())
                .signedBlockHeader(
                    DataColumnSidecarFulu.required(dataColumnSidecar).getSignedBlockHeader())
                .kzgCommitmentsInclusionProof(
                    DataColumnSidecarFulu.required(dataColumnSidecar)
                        .getKzgCommitmentsInclusionProof()
                        .asListUnboxed()
                        .stream()
                        .map(Bytes32::not) // modify inclusion proof list
                        .toList()));
  }

  public static DataColumnSidecar breakValidity(final DataColumnSidecar dataColumnSidecar) {
    final DataColumnSidecarSchemaFulu dataColumnSidecarSchemaFulu =
        DataColumnSidecarSchemaFulu.required(
            (DataColumnSidecarSchema<?>) dataColumnSidecar.getSchema());
    return dataColumnSidecarSchemaFulu.create(
        builder ->
            builder
                .index(dataColumnSidecar.getIndex())
                .column(dataColumnSidecar.getColumn())
                .kzgCommitments(dataColumnSidecar.getKzgCommitments())
                // wrong number of proofs
                .kzgProofs(
                    dataColumnSidecar
                        .getKzgProofs()
                        .getSchema()
                        .createFromElements(
                            dataColumnSidecar.getKzgProofs().stream().skip(1).toList()))
                .signedBlockHeader(
                    DataColumnSidecarFulu.required(dataColumnSidecar).getSignedBlockHeader())
                .kzgCommitmentsInclusionProof(
                    DataColumnSidecarFulu.required(dataColumnSidecar)
                        .getKzgCommitmentsInclusionProof()
                        .asListUnboxed()));
  }
}
