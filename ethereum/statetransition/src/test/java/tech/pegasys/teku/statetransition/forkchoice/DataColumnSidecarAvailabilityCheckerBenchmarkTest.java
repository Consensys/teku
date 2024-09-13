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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZGAbstractBenchmark;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobKzgCommitmentsSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.DataAndValidationResult;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.datacolumns.DataAvailabilitySampler;

@Disabled("Benchmark")
public class DataColumnSidecarAvailabilityCheckerBenchmarkTest extends KZGAbstractBenchmark {
  private final DataAvailabilitySampler dataAvailabilitySampler =
      mock(DataAvailabilitySampler.class);
  private final ReadOnlyStore store = mock(ReadOnlyStore.class);
  private static final int ROUNDS = 10;

  private final Spec spec = TestSpecFactory.createMinimalEip7594();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  @Test
  public void benchmarkValidateImmediately() {
    final List<Blob> blobs =
        IntStream.range(0, 6).mapToObj(__ -> dataStructureUtil.randomValidBlob()).toList();
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
        spec.forMilestone(SpecMilestone.EIP7594)
            .miscHelpers()
            .toVersionEip7594()
            .orElseThrow()
            .constructDataColumnSidecars(signedBeaconBlock, blobs, getKzg());

    final List<Integer> validationTimes = new ArrayList<>();
    for (int i = 0; i < ROUNDS; i++) {
      final long start = System.currentTimeMillis();
      final DataColumnSidecarAvailabilityChecker dataColumnSidecarAvailabilityChecker =
          new DataColumnSidecarAvailabilityChecker(
              dataAvailabilitySampler, store, getKzg(), spec, signedBeaconBlock);
      final DataAndValidationResult<DataColumnSidecar> dataColumnSidecarDataAndValidationResult =
          dataColumnSidecarAvailabilityChecker.validateImmediately(dataColumnSidecars);
      assertThat(dataColumnSidecarDataAndValidationResult.isValid()).isTrue();
      final long end = System.currentTimeMillis();
      validationTimes.add((int) (end - start));
    }

    printStats(validationTimes);
  }
}
