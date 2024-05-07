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

package tech.pegasys.teku.reference.phase0.kzg;

import com.google.common.collect.ImmutableMap;
import tech.pegasys.teku.reference.TestExecutor;

public class KzgTests {

  public static final ImmutableMap<String, TestExecutor> KZG_TEST_TYPES =
      ImmutableMap.<String, TestExecutor>builder()

          // BlobSidecar Deneb utils
          .put("kzg/blob_to_kzg_commitment", new KzgBlobToCommitmentTestExecutor())
          .put("kzg/compute_blob_kzg_proof", new KzgComputeBlobProofTestExecutor())
          // no KZG interface on CL side, EL responsibility
          .put("kzg/compute_kzg_proof", TestExecutor.IGNORE_TESTS)
          // actually uses verify_blob_kzg_proof_batch KZG interface
          .put("kzg/verify_blob_kzg_proof", new KzgVerifyBlobProofTestExecutor())
          .put("kzg/verify_blob_kzg_proof_batch", new KzgVerifyBlobProofBatchTestExecutor())
          // no KZG interface on CL side, EL responsibility
          .put("kzg/verify_kzg_proof", TestExecutor.IGNORE_TESTS)

          // DataColumnSidecar EIP-7594 utils
          .put("kzg/compute_cells", new KzgComputeCellsTestExecutor())
          .put("kzg/compute_cells_and_kzg_proofs", new KzgComputeCellsAndKzgProofsTestExecutor())
          .put("kzg/recover_all_cells", new KzgRecoverAllCellsTestExecutor())
          .put("kzg/verify_cell_kzg_proof", new KzgVerifyCellKzgProofTestExecutor())
          .put("kzg/verify_cell_kzg_proof_batch", new KzgVerifyCellKzgProofBatchTestExecutor())
          .build();
}
