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

package tech.pegasys.teku.spec.logic.versions.eip7594.helpers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZGAbstractBenchmark;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobKzgCommitmentsSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.MatrixEntry;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsEip7594;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class MiscHelpersEip7594Test extends KZGAbstractBenchmark {

  private final Spec spec =
      TestSpecFactory.createMinimalEip7594(
          builder ->
              builder.eip7594Builder(
                  eip7594Builder -> eip7594Builder.numberOfColumns(128).samplesPerSlot(16)));
  private final Predicates predicates = new Predicates(spec.getGenesisSpecConfig());
  private final SchemaDefinitionsEip7594 schemaDefinitionsEip7594 =
      SchemaDefinitionsEip7594.required(spec.getGenesisSchemaDefinitions());
  private final MiscHelpersEip7594 miscHelpersEip7594 =
      new MiscHelpersEip7594(
          spec.getGenesisSpecConfig().toVersionEip7594().orElseThrow(),
          predicates,
          schemaDefinitionsEip7594);

  @ParameterizedTest(name = "{0} allowed failure(s)")
  @MethodSource("getExtendedSampleCountFixtures")
  public void getExtendedSampleCountReturnsCorrectValues(
      final int allowedFailures, final int numberOfSamples) {
    assertThat(miscHelpersEip7594.getExtendedSampleCount(UInt64.valueOf(allowedFailures)))
        .isEqualTo(UInt64.valueOf(numberOfSamples));
  }

  @Test
  public void getExtendedSampleCountShouldThrowWhenAllowedFailuresTooBig() {
    assertThatThrownBy(() -> miscHelpersEip7594.getExtendedSampleCount(UInt64.valueOf(65)))
        .isOfAnyClassIn(IllegalArgumentException.class)
        .hasMessageStartingWith(
            "Allowed failures (65) should be less than half of columns number (128)");
  }

  @Test
  @Disabled("Benchmark")
  public void benchmarkComputeExtendedMatrix() {
    final int numberOfRounds = 10;
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
    final List<Blob> blobs =
        IntStream.range(0, 6).mapToObj(__ -> dataStructureUtil.randomValidBlob()).toList();
    final List<Integer> runTimes = new ArrayList<>();
    for (int i = 0; i < numberOfRounds; i++) {
      final long start = System.currentTimeMillis();
      final List<List<MatrixEntry>> extendedMatrix =
          miscHelpersEip7594.computeExtendedMatrix(blobs, getKzg());
      assertEquals(6, extendedMatrix.size());
      final long end = System.currentTimeMillis();
      runTimes.add((int) (end - start));
    }
    printStats(runTimes);
  }

  @Test
  @Disabled("Benchmark")
  public void benchmarkConstructDataColumnSidecarsWithExtendedMatrix() {
    final int numberOfRounds = 10;
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
    final List<Blob> blobs =
        IntStream.range(0, 6).mapToObj(__ -> dataStructureUtil.randomValidBlob()).toList();
    final List<List<MatrixEntry>> extendedMatrix =
        miscHelpersEip7594.computeExtendedMatrix(blobs, getKzg());
    final List<SszKZGCommitment> kzgCommitments =
        blobs.stream()
            .map(blob -> getKzg().blobToKzgCommitment(blob.getBytes()))
            .map(SszKZGCommitment::new)
            .toList();
    final BlobKzgCommitmentsSchema blobKzgCommitmentsSchema =
        SchemaDefinitionsDeneb.required(spec.atSlot(UInt64.ONE).getSchemaDefinitions())
            .getBlobKzgCommitmentsSchema();
    final SignedBeaconBlock signedBeaconBlock =
        dataStructureUtil.randomSignedBeaconBlockWithCommitments(
            blobKzgCommitmentsSchema.createFromElements(kzgCommitments));

    final List<Integer> runTimes = new ArrayList<>();
    for (int i = 0; i < numberOfRounds; i++) {
      final long start = System.currentTimeMillis();
      List<DataColumnSidecar> dataColumnSidecars =
          miscHelpersEip7594.constructDataColumnSidecars(
              signedBeaconBlock.getMessage(), signedBeaconBlock.asHeader(), extendedMatrix);
      assertEquals(blobs.size(), dataColumnSidecars.getFirst().getDataColumn().size());
      final long end = System.currentTimeMillis();
      runTimes.add((int) (end - start));
    }
    printStats(runTimes);
  }

  @Test
  public void emptyInclusionProof_shouldFailValidation() {
    final Predicates predicatesMock = mock(Predicates.class);
    when(predicatesMock.isValidMerkleBranch(any(), any(), anyInt(), anyInt(), any()))
        .thenReturn(true);
    final MiscHelpersEip7594 miscHelpersEip7594WithMockPredicates =
        new MiscHelpersEip7594(
            spec.getGenesisSpecConfig().toVersionEip7594().orElseThrow(),
            predicatesMock,
            schemaDefinitionsEip7594);
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
    final DataColumnSidecar dataColumnSidecar =
        schemaDefinitionsEip7594
            .getDataColumnSidecarSchema()
            .create(
                UInt64.ZERO,
                schemaDefinitionsEip7594.getDataColumnSchema().create(List.of()),
                List.of(),
                List.of(),
                dataStructureUtil.randomSignedBeaconBlockHeader(),
                List.of(
                    dataStructureUtil.randomBytes32(),
                    dataStructureUtil.randomBytes32(),
                    dataStructureUtil.randomBytes32(),
                    dataStructureUtil.randomBytes32()));

    assertThat(
            predicatesMock.isValidMerkleBranch(
                dataColumnSidecar.getSszKZGCommitments().hashTreeRoot(),
                dataColumnSidecar.getKzgCommitmentsInclusionProof(),
                spec.getGenesisSpecConfig()
                    .toVersionEip7594()
                    .orElseThrow()
                    .getKzgCommitmentsInclusionProofDepth()
                    .intValue(),
                miscHelpersEip7594WithMockPredicates.getBlockBodyKzgCommitmentsGeneralizedIndex(),
                dataColumnSidecar.getBlockBodyRoot()))
        .isTrue();
    assertThat(
            miscHelpersEip7594WithMockPredicates.verifyDataColumnSidecarInclusionProof(
                dataColumnSidecar))
        .isFalse();
  }

  @Test
  public void emptyInclusionProofFromRealNetwork_shouldFailValidation() {
    final Spec specMainnet = TestSpecFactory.createMainnetEip7594();
    final Predicates predicatesMainnet = new Predicates(specMainnet.getGenesisSpecConfig());
    final SchemaDefinitionsEip7594 schemaDefinitionsEip7594Mainnet =
        SchemaDefinitionsEip7594.required(specMainnet.getGenesisSchemaDefinitions());
    final MiscHelpersEip7594 miscHelpersEip7594Mainnet =
        new MiscHelpersEip7594(
            specMainnet.getGenesisSpecConfig().toVersionEip7594().orElseThrow(),
            predicatesMainnet,
            schemaDefinitionsEip7594Mainnet);
    final DataColumnSidecar dataColumnSidecar =
        schemaDefinitionsEip7594Mainnet
            .getDataColumnSidecarSchema()
            .create(
                UInt64.ZERO,
                schemaDefinitionsEip7594Mainnet.getDataColumnSchema().create(List.of()),
                List.of(),
                List.of(),
                new SignedBeaconBlockHeader(
                    new BeaconBlockHeader(
                        UInt64.valueOf(37),
                        UInt64.valueOf(3426),
                        Bytes32.fromHexString(
                            "0x6d3091dae0e2a0251cc2c0d9fef846e1c6e685f18fc8a2c7734f25750c22da36"),
                        Bytes32.fromHexString(
                            "0x715f24108254c3fcbef60c739fe702aed3ee692cb223c884b3db6e041c56c2a6"),
                        Bytes32.fromHexString(
                            "0xbea87258cde49915c8c929b6b91fbbcde004aeaaa08a3ccdc3248dc62b0e682f")),
                    BLSSignature.fromBytesCompressed(
                        Bytes.fromHexString(
                            "0xb4c313365edbc7cfa9319c54ecba0a8dc54c8537752c72a86c762eb0a81b3ad1eda43f0f3b19a9c9523a6a42450c1d070556e0a443d4733922765764ef5850b41d20b4f6af6cc93a70eb1023cc63473f111de772315a2726406be9dc6cb24e67"))),
                List.of(
                    Bytes32.fromHexString(
                        "0x792930bbd5baac43bcc798ee49aa8185ef76bb3b44ba62b91d86ae569e4bb535"),
                    Bytes32.fromHexString(
                        "0xcd581849371d5f91b7d02a366b23402397007b50180069584f2bd4e14397540b"),
                    Bytes32.fromHexString(
                        "0xdb56114e00fdd4c1f85c892bf35ac9a89289aaecb1ebd0a96cde606a748b5d71"),
                    Bytes32.fromHexString(
                        "0x9535c3eb42aaf182b13b18aacbcbc1df6593ecafd0bf7d5e94fb727b2dc1f265")));
    assertThat(miscHelpersEip7594Mainnet.verifyDataColumnSidecarInclusionProof(dataColumnSidecar))
        .isFalse();
  }

  static Stream<Arguments> getExtendedSampleCountFixtures() throws IOException {
    return Stream.of(
        Arguments.of(0, 16),
        Arguments.of(1, 20),
        Arguments.of(2, 24),
        Arguments.of(3, 27),
        Arguments.of(4, 29),
        Arguments.of(5, 32),
        Arguments.of(6, 35),
        Arguments.of(7, 37),
        Arguments.of(8, 40),
        Arguments.of(64, 128));
  }
}
