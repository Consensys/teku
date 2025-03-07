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

import java.util.List;
import org.apache.tuweni.bytes.Bytes;

/**
 * Rust KZG library with fallback to C-KZG4844 on non-implemented methods (EIP-4844 methods are
 * currently not implemented in Rust library)
 *
 * <p>This class should be a singleton
 */
final class RustWithCKZG implements KZG {

  @SuppressWarnings("NonFinalStaticField")
  private static RustWithCKZG instance;

  private final CKZG4844 ckzg4844Delegate;
  private final RustKZG rustKzgDelegeate;

  static synchronized RustWithCKZG getInstance() {
    if (instance == null) {
      instance = new RustWithCKZG();
    }
    return instance;
  }

  private RustWithCKZG() {
    this.ckzg4844Delegate = CKZG4844.getInstance();
    this.rustKzgDelegeate = RustKZG.getInstance();
  }

  @Override
  public synchronized void loadTrustedSetup(final String trustedSetupFile) throws KZGException {
    ckzg4844Delegate.loadTrustedSetup(trustedSetupFile);
    rustKzgDelegeate.loadTrustedSetup(trustedSetupFile);
  }

  @Override
  public synchronized void freeTrustedSetup() throws KZGException {
    KZGException ckzg4844DelegateException = null;
    try {
      ckzg4844Delegate.freeTrustedSetup();
    } catch (final KZGException ex) {
      ckzg4844DelegateException = ex;
    }
    KZGException rustKzgDelegateException = null;
    try {
      rustKzgDelegeate.freeTrustedSetup();
    } catch (final KZGException ex) {
      rustKzgDelegateException = ex;
    }
    if (ckzg4844DelegateException != null || rustKzgDelegateException != null) {
      if (ckzg4844DelegateException != null && rustKzgDelegateException != null) {
        throw new KZGException(
            "RustKZG and CKZG4844 libraries failed to free trusted setup",
            ckzg4844DelegateException);
      } else if (ckzg4844DelegateException != null) {
        throw ckzg4844DelegateException;
      } else {
        throw rustKzgDelegateException;
      }
    }
  }

  @Override
  public boolean verifyBlobKzgProof(
      final Bytes blob, final KZGCommitment kzgCommitment, final KZGProof kzgProof)
      throws KZGException {
    return ckzg4844Delegate.verifyBlobKzgProof(blob, kzgCommitment, kzgProof);
  }

  @Override
  public boolean verifyBlobKzgProofBatch(
      final List<Bytes> blobs,
      final List<KZGCommitment> kzgCommitments,
      final List<KZGProof> kzgProofs)
      throws KZGException {
    return ckzg4844Delegate.verifyBlobKzgProofBatch(blobs, kzgCommitments, kzgProofs);
  }

  @Override
  public KZGCommitment blobToKzgCommitment(final Bytes blob) throws KZGException {
    return ckzg4844Delegate.blobToKzgCommitment(blob);
  }

  @Override
  public KZGProof computeBlobKzgProof(final Bytes blob, final KZGCommitment kzgCommitment)
      throws KZGException {
    return ckzg4844Delegate.computeBlobKzgProof(blob, kzgCommitment);
  }

  @Override
  public List<KZGCellAndProof> computeCellsAndProofs(final Bytes blob) {
    return rustKzgDelegeate.computeCellsAndProofs(blob);
  }

  @Override
  public boolean verifyCellProofBatch(
      final List<KZGCommitment> commitments,
      final List<KZGCellWithColumnId> cellWithIds,
      final List<KZGProof> proofs) {
    return rustKzgDelegeate.verifyCellProofBatch(commitments, cellWithIds, proofs);
  }

  @Override
  public List<KZGCellAndProof> recoverCellsAndProofs(final List<KZGCellWithColumnId> cells) {
    return rustKzgDelegeate.recoverCellsAndProofs(cells);
  }
}
