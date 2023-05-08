/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.ethereum.executionlayer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes48;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.kzg.KZGCommitment;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobsBundle;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.logic.versions.deneb.helpers.MiscHelpersDeneb;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class BlobsBundleValidatorTest {
  private final Spec spec = TestSpecFactory.createMinimalDeneb();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final MiscHelpersDeneb miscHelpers = mock(MiscHelpersDeneb.class);
  private final BlobsBundleValidator blobsBundleValidator =
      new BlobsBundleValidatorImpl(miscHelpers);

  @Test
  public void shouldVerifyAgainstPayloadTransactions() throws Exception {
    final BlobsBundle blobsBundle =
        new BlobsBundle(Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    final ExecutionPayload executionPayload = dataStructureUtil.randomExecutionPayload();
    when(miscHelpers.verifyKZGCommitmentsAgainstTransactions(any(), any())).thenReturn(false);
    assertThatThrownBy(
            () -> blobsBundleValidator.validate(blobsBundle, Optional.of(executionPayload)))
        .isInstanceOf(BlobsBundleValidationException.class)
        .hasMessage("KZG commitments doesn't match the versioned hashes in the transactions");
    when(miscHelpers.verifyKZGCommitmentsAgainstTransactions(any(), any())).thenReturn(true);
    blobsBundleValidator.validate(blobsBundle, Optional.of(executionPayload));
  }

  @Test
  public void shouldMatchSizesOfCommitmentsAndBlobs() {
    when(miscHelpers.verifyKZGCommitmentsAgainstTransactions(any(), any())).thenReturn(true);
    final BlobsBundle blobsBundle =
        new BlobsBundle(
            List.of(dataStructureUtil.randomKZGCommitment()),
            List.of(dataStructureUtil.randomKZGProof()),
            Collections.emptyList());
    assertThatThrownBy(() -> blobsBundleValidator.validate(blobsBundle, Optional.empty()))
        .isInstanceOf(BlobsBundleValidationException.class)
        .hasMessage("KZG commitments size doesn't match blobs size");
  }

  @Test
  public void shouldVerifyAllBlobsAgainstKzgCommitments() throws Exception {
    final KZGCommitment commitment = new KZGCommitment(Bytes48.leftPad(Bytes.fromHexString("a0")));
    when(miscHelpers.verifyKZGCommitmentsAgainstTransactions(any(), any())).thenReturn(true);
    when(miscHelpers.blobToKzgCommitment(any())).thenReturn(commitment);

    final BlobsBundle blobsBundle = dataStructureUtil.randomBlobsBundle();
    assertThatThrownBy(() -> blobsBundleValidator.validate(blobsBundle, Optional.empty()))
        .isInstanceOf(BlobsBundleValidationException.class)
        .hasMessage("Blobs not matching KZG commitments");

    final int numberOfBlobs = blobsBundle.getBlobs().size();
    final BlobsBundle blobsBundle2 =
        new BlobsBundle(
            IntStream.range(0, numberOfBlobs)
                .mapToObj(__ -> commitment)
                .collect(Collectors.toList()),
            dataStructureUtil.randomKZGProofs(numberOfBlobs),
            blobsBundle.getBlobs());
    new BlobsBundle(blobsBundle.getCommitments(), blobsBundle.getProofs(), blobsBundle.getBlobs());
    blobsBundleValidator.validate(blobsBundle2, Optional.empty());
  }
}
