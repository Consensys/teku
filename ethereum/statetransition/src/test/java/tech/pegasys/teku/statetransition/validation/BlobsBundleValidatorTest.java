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

package tech.pegasys.teku.statetransition.validation;

import static org.assertj.core.api.Assertions.assertThat;
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
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip4844.BlobsBundle;
import tech.pegasys.teku.spec.logic.versions.eip4844.helpers.MiscHelpersEip4844;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class BlobsBundleValidatorTest {
  private final Spec spec = TestSpecFactory.createMinimalEip4844();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final MiscHelpersEip4844 miscHelpers = mock(MiscHelpersEip4844.class);
  private final BlobsBundleValidator blobsBundleValidator = new BlobsBundleValidator(miscHelpers);

  @Test
  public void shouldVerifyAgainstPayloadTransactions() {
    final BlobsBundle blobsBundle =
        new BlobsBundle(
            dataStructureUtil.randomBytes32(), Collections.emptyList(), Collections.emptyList());
    final ExecutionPayload executionPayload = dataStructureUtil.randomExecutionPayload();
    when(miscHelpers.verifyKZGCommitmentsAgainstTransactions(any(), any())).thenReturn(false);
    assertThat(blobsBundleValidator.validate(blobsBundle, Optional.of(executionPayload)))
        .matches(InternalValidationResult::isReject);
    when(miscHelpers.verifyKZGCommitmentsAgainstTransactions(any(), any())).thenReturn(true);
    assertThat(blobsBundleValidator.validate(blobsBundle, Optional.of(executionPayload)))
        .matches(InternalValidationResult::isAccept);
  }

  @Test
  public void shouldMatchSizesOfKzgsAndBlobs() {
    when(miscHelpers.verifyKZGCommitmentsAgainstTransactions(any(), any())).thenReturn(true);
    final BlobsBundle blobsBundle =
        new BlobsBundle(
            dataStructureUtil.randomBytes32(),
            List.of(dataStructureUtil.randomKZGCommitment()),
            Collections.emptyList());
    assertThat(blobsBundleValidator.validate(blobsBundle, Optional.empty()))
        .matches(
            internalValidationResult ->
                internalValidationResult.isReject()
                    && internalValidationResult
                        .getDescription()
                        .orElseThrow()
                        .equals("KZG commitments size doesn't match blobs size"));
  }

  @Test
  public void shouldVerifyAllBlobsAgainstKzgCommitments() {
    final KZGCommitment commitment = new KZGCommitment(Bytes48.leftPad(Bytes.fromHexString("a0")));
    when(miscHelpers.verifyKZGCommitmentsAgainstTransactions(any(), any())).thenReturn(true);
    when(miscHelpers.blobToKzgCommitment(any())).thenReturn(commitment);

    final BlobsBundle blobsBundle = dataStructureUtil.randomBlobsBundle();
    assertThat(blobsBundleValidator.validate(blobsBundle, Optional.empty()))
        .matches(
            internalValidationResult ->
                internalValidationResult.isReject()
                    && internalValidationResult
                        .getDescription()
                        .orElseThrow()
                        .equals("Blobs not matching KZG commitments"));

    final BlobsBundle blobsBundle2 =
        new BlobsBundle(
            blobsBundle.getBlockHash(),
            IntStream.range(0, blobsBundle.getKzgs().size())
                .mapToObj(__ -> commitment)
                .collect(Collectors.toList()),
            blobsBundle.getBlobs());
    assertThat(blobsBundleValidator.validate(blobsBundle2, Optional.empty()))
        .matches(InternalValidationResult::isAccept);
  }
}
