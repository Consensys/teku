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

package tech.pegasys.teku.spec.logic.versions.fulu.helpers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import it.unimi.dsi.fastutil.ints.IntList;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZGAbstractBenchmark;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.BlobScheduleEntry;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobKzgCommitmentsSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.MatrixEntry;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.state.BeaconStateTestBuilder;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.PredicatesElectra;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsFulu;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class MiscHelpersFuluTest extends KZGAbstractBenchmark {

  private final Spec spec =
      TestSpecFactory.createMinimalFulu(
          builder ->
              builder.fuluBuilder(
                  fuluBuilder ->
                      fuluBuilder
                          .numberOfColumns(128)
                          .numberOfCustodyGroups(128)
                          .custodyRequirement(4)
                          .validatorCustodyRequirement(8)
                          .balancePerAdditionalCustodyGroup(UInt64.valueOf(32000000000L))
                          .samplesPerSlot(16)));
  private final SpecConfig specConfig = spec.atSlot(ZERO).getConfig();
  private final PredicatesElectra predicates = new PredicatesElectra(spec.getGenesisSpecConfig());
  private final SchemaDefinitionsFulu schemaDefinitionsFulu =
      SchemaDefinitionsFulu.required(spec.getGenesisSchemaDefinitions());
  private final MiscHelpersFulu miscHelpersFulu =
      new MiscHelpersFulu(
          spec.getGenesisSpecConfig().toVersionFulu().orElseThrow(),
          predicates,
          schemaDefinitionsFulu);

  @ParameterizedTest(name = "{0} allowed failure(s)")
  @MethodSource("getExtendedSampleCountFixtures")
  public void getExtendedSampleCountReturnsCorrectValues(
      final int allowedFailures, final int numberOfSamples) {
    assertThat(miscHelpersFulu.getExtendedSampleCount(UInt64.valueOf(allowedFailures)))
        .isEqualTo(UInt64.valueOf(numberOfSamples));
  }

  @Test
  public void getExtendedSampleCountShouldThrowWhenAllowedFailuresTooBig() {
    assertThatThrownBy(() -> miscHelpersFulu.getExtendedSampleCount(UInt64.valueOf(65)))
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
          miscHelpersFulu.computeExtendedMatrixAndProofs(blobs, getKzg());
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
        miscHelpersFulu.computeExtendedMatrixAndProofs(blobs, getKzg());
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
          miscHelpersFulu.constructDataColumnSidecars(
              signedBeaconBlock.getMessage(), signedBeaconBlock.asHeader(), extendedMatrix);
      assertEquals(blobs.size(), dataColumnSidecars.getFirst().getDataColumn().size());
      final long end = System.currentTimeMillis();
      runTimes.add((int) (end - start));
    }
    printStats(runTimes);
  }

  @ParameterizedTest
  @MethodSource("getComputeForkDigestFuluScenarios")
  public void computeForkDigestFuluTest(
      final Spec spec, final long epoch, final String expectedValue) {
    assertThat(
            MiscHelpersFulu.required(spec.atEpoch(UInt64.valueOf(epoch)).miscHelpers())
                .computeForkDigest(Bytes32.ZERO, UInt64.valueOf(epoch)))
        .isEqualTo(Bytes4.fromHexString(expectedValue));
  }

  // Scenarios from
  // https://github.com/ethereum/consensus-specs/blob/dev/tests/core/pyspec/eth2spec/test/fulu/validator/test_compute_fork_digest.py
  public static Stream<Arguments> getComputeForkDigestFuluScenarios() {
    final Spec spec =
        TestSpecFactory.createMinimalFulu(
            b ->
                b.electraBuilder(
                        eb -> eb.electraForkEpoch(UInt64.valueOf(9)).maxBlobsPerBlockElectra(9))
                    .fuluBuilder(
                        fb ->
                            fb.fuluForkEpoch(UInt64.valueOf(100))
                                .blobSchedule(
                                    List.of(
                                        new BlobScheduleEntry(UInt64.valueOf(100), 100),
                                        new BlobScheduleEntry(UInt64.valueOf(150), 175),
                                        new BlobScheduleEntry(UInt64.valueOf(200), 200),
                                        new BlobScheduleEntry(UInt64.valueOf(250), 275),
                                        new BlobScheduleEntry(UInt64.valueOf(300), 300)))));

    return Stream.of(
        Arguments.of(spec, 100, "44a571e8"),
        Arguments.of(spec, 101, "44a571e8"),
        Arguments.of(spec, 150, "1171afca"),
        Arguments.of(spec, 200, "427a30ab"),
        Arguments.of(spec, 250, "d5310ef1"),
        Arguments.of(spec, 299, "d5310ef1"),
        Arguments.of(spec, 300, "51d229f7"));
  }

  @Test
  @Disabled("Benchmark")
  public void benchmarkVerifyDataColumnSidecarKzgProof() {
    final Spec spec = TestSpecFactory.createMainnetFulu();
    final int numberOfRounds = 25;
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
    final SchemaDefinitionsFulu schemaDefinitionsFulu =
        SchemaDefinitionsFulu.required(spec.getGenesisSchemaDefinitions());
    final MiscHelpersFulu miscHelpersFulu =
        new MiscHelpersFulu(
            spec.getGenesisSpecConfig().toVersionFulu().orElseThrow(),
            predicates,
            schemaDefinitionsFulu);
    final List<Blob> blobs =
        IntStream.range(0, 72).mapToObj(__ -> dataStructureUtil.randomValidBlob()).toList();
    final List<List<MatrixEntry>> extendedMatrix =
        miscHelpersFulu.computeExtendedMatrixAndProofs(blobs, getKzg());
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
    final List<DataColumnSidecar> dataColumnSidecars =
        miscHelpersFulu.constructDataColumnSidecars(
            signedBeaconBlock.getMessage(), signedBeaconBlock.asHeader(), extendedMatrix);

    final List<Integer> runTimes = new ArrayList<>();
    System.out.printf("Running verifyDataColumnSidecarKzgProof with %s blobs\n", blobs.size());
    for (int i = 0; i < numberOfRounds; i++) {
      final long start = System.currentTimeMillis();
      dataColumnSidecars.stream()
          .parallel()
          .forEach(
              dataColumnSidecar ->
                  miscHelpersFulu.verifyDataColumnSidecarKzgProof(getKzg(), dataColumnSidecar));
      final long end = System.currentTimeMillis();
      runTimes.add((int) (end - start));
    }
    printStats(runTimes);
  }

  @Test
  public void emptyInclusionProof_shouldFailValidation() {
    final PredicatesElectra predicatesMock = mock(PredicatesElectra.class);
    when(predicatesMock.toVersionElectra()).thenReturn(Optional.of(predicatesMock));
    when(predicatesMock.isValidMerkleBranch(any(), any(), anyInt(), anyInt(), any()))
        .thenReturn(true);
    final MiscHelpersFulu miscHelpersFuluWithMockPredicates =
        new MiscHelpersFulu(
            spec.getGenesisSpecConfig().toVersionFulu().orElseThrow(),
            predicatesMock,
            schemaDefinitionsFulu);
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
    final DataColumnSidecar dataColumnSidecar =
        SchemaDefinitionsFulu.required(schemaDefinitionsFulu)
            .getDataColumnSidecarSchema()
            .create(
                ZERO,
                SchemaDefinitionsFulu.required(schemaDefinitionsFulu)
                    .getDataColumnSchema()
                    .create(List.of()),
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
                    .toVersionFulu()
                    .orElseThrow()
                    .getKzgCommitmentsInclusionProofDepth()
                    .intValue(),
                miscHelpersFuluWithMockPredicates.getBlockBodyKzgCommitmentsGeneralizedIndex(),
                dataColumnSidecar.getBlockBodyRoot()))
        .isTrue();
    assertThat(
            miscHelpersFuluWithMockPredicates.verifyDataColumnSidecarInclusionProof(
                dataColumnSidecar))
        .isFalse();
  }

  @Test
  public void emptyInclusionProofFromRealNetwork_shouldFailValidation() {
    final Spec specMainnet = TestSpecFactory.createMainnetFulu();
    final PredicatesElectra predicatesMainnet =
        new PredicatesElectra(specMainnet.getGenesisSpecConfig());
    final SchemaDefinitionsFulu schemaDefinitionsFuluMainnet =
        SchemaDefinitionsFulu.required(specMainnet.getGenesisSchemaDefinitions());
    final MiscHelpersFulu miscHelpersFuluMainnet =
        new MiscHelpersFulu(
            specMainnet.getGenesisSpecConfig().toVersionFulu().orElseThrow(),
            predicatesMainnet,
            schemaDefinitionsFuluMainnet);
    final DataColumnSidecar dataColumnSidecar =
        SchemaDefinitionsFulu.required(schemaDefinitionsFuluMainnet)
            .getDataColumnSidecarSchema()
            .create(
                ZERO,
                SchemaDefinitionsFulu.required(schemaDefinitionsFuluMainnet)
                    .getDataColumnSchema()
                    .create(List.of()),
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
    assertThat(miscHelpersFuluMainnet.verifyDataColumnSidecarInclusionProof(dataColumnSidecar))
        .isFalse();
  }

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  @ParameterizedTest(name = "{0} validator custody groups required")
  @MethodSource("getValidatorCustodyRequirementFixtures")
  public void testGetValidatorCustodyRequirement(
      final int expectedValidatorCustodyCount, final long[] validatorBalancesEth) {
    BeaconStateTestBuilder beaconStateTestBuilder =
        new BeaconStateTestBuilder(dataStructureUtil)
            .forkVersion(specConfig.getGenesisForkVersion());

    LongStream.of(validatorBalancesEth)
        .mapToObj(balance -> UInt64.valueOf(balance).times(1_000_000_000L))
        .forEach(beaconStateTestBuilder::activeConsolidatingValidator);
    BeaconState state = beaconStateTestBuilder.build();

    final Set<UInt64> validatorIndicesSet =
        IntStream.range(0, validatorBalancesEth.length)
            .mapToObj(UInt64::valueOf)
            .collect(Collectors.toSet());
    assertEquals(
        UInt64.valueOf(expectedValidatorCustodyCount),
        miscHelpersFulu.getValidatorsCustodyRequirement(state, validatorIndicesSet));
  }

  @Test
  public void computeProposerIndices_returnsListWithSlotsPerEpochSize() {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
    final BeaconState state = dataStructureUtil.randomBeaconState();
    final UInt64 epoch = UInt64.ONE;
    final Bytes32 epochSeed = Bytes32.random();
    final int slotsPerEpoch = spec.getGenesisSpecConfig().getSlotsPerEpoch();
    final List<Integer> activeValidatorIndices = IntStream.range(0, 10).boxed().toList();

    final IntList activeValidatorIndicesIntList =
        IntList.of(activeValidatorIndices.stream().mapToInt(Integer::intValue).toArray());

    List<Integer> proposerIndices =
        miscHelpersFulu.computeProposerIndices(
            state, epoch, epochSeed, activeValidatorIndicesIntList);

    assertThat(proposerIndices).hasSize(slotsPerEpoch);
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

  static Stream<Arguments> getValidatorCustodyRequirementFixtures() throws IOException {
    return Stream.of(
        // expectedValidatorCustodyCount, validatorBalancesEth
        Arguments.of(8, new long[] {31}),
        Arguments.of(8, new long[] {32}),
        Arguments.of(8, new long[] {32, 32, 33, 34, 33, 32}),
        Arguments.of(15, new long[] {32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32}),
        Arguments.of(9, new long[] {48, 48, 48, 48, 48, 48}),
        Arguments.of(8, new long[] {48, 48}),
        Arguments.of(128, new long[] {32, 48, 1024, 2048, 1024}),
        Arguments.of(128, new long[] {2048, 2048}));
  }
}
