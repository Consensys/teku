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

package tech.pegasys.teku.kzg;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

public final class RustKZGTest extends KZGAbstractTest {

  public RustKZGTest() {
    super(RustKZG.getInstance());
  }

  @Test
  @Override
  @SuppressWarnings("JavaCase")
  public void testUsageWithoutLoadedTrustedSetup_shouldThrowException() {
    kzg.freeTrustedSetup();
    final Bytes blob = getSampleBlob();
    assertThatThrownBy(() -> kzg.computeCellsAndProofs(blob))
        .isOfAnyClassIn(IllegalStateException.class)
        .hasMessageStartingWith("KZG context context has been destroyed");
  }

  @Test
  @Override
  public void testComputingAndVerifyingBatchProofs() {
    assertThatThrownBy(super::testComputingAndVerifyingBatchProofs)
        .isOfAnyClassIn(RuntimeException.class)
        .hasMessageStartingWith("LibPeerDASKZG library doesn't support");
  }

  @Test
  @Override
  public void testVerifyingEmptyBatch() {
    assertThatThrownBy(super::testVerifyingEmptyBatch)
        .isOfAnyClassIn(RuntimeException.class)
        .hasMessageStartingWith("LibPeerDASKZG library doesn't support");
  }

  @Test
  @Override
  public void testComputingAndVerifyingSingleProof() {
    assertThatThrownBy(super::testComputingAndVerifyingSingleProof)
        .isOfAnyClassIn(RuntimeException.class)
        .hasMessageStartingWith("LibPeerDASKZG library doesn't support");
  }

  @Test
  @Override
  public void testComputingAndVerifyingBatchSingleProof() {
    assertThatThrownBy(super::testComputingAndVerifyingBatchSingleProof)
        .isOfAnyClassIn(RuntimeException.class)
        .hasMessageStartingWith("LibPeerDASKZG library doesn't support");
  }

  @Test
  @Override
  public void testVerifyingBatchProofsThrowsIfSizesDoesntMatch() {
    assertThatThrownBy(super::testVerifyingBatchProofsThrowsIfSizesDoesntMatch)
        .isOfAnyClassIn(RuntimeException.class)
        .hasMessageStartingWith("LibPeerDASKZG library doesn't support");
  }

  @Override
  public void testComputingProofWithIncorrectLengthBlobDoesNotCauseSegfault(final String blobHex) {
    // skip, not supported
  }

  @Override
  public void incorrectTrustedSetupFilesShouldThrow(final String filename) {
    // skip, not supported
  }

  @Override
  public void monomialTrustedSetupFilesShouldThrow() {
    // skip, not supported
  }

  @Override
  public void testInvalidLengthG2PointInNewTrustedSetup() {
    // skip, not supported
  }

  @Test
  @Override
  public void testComputeAndVerifyCellProof() {
    assertThatThrownBy(super::testComputeAndVerifyCellProof)
        .isOfAnyClassIn(RuntimeException.class)
        .hasMessageStartingWith("LibPeerDASKZG library doesn't support");
  }
}
