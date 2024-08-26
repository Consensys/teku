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

import com.google.common.collect.Streams;
import ethereum.cryptography.CellsAndProofs;
import ethereum.cryptography.LibPeerDASKZG;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes48;

/**
 * Wrapper around LibPeerDASKZG Rust PeerDAS library
 *
 * <p>This class should be a singleton
 */
final class RustKZG implements KZG {

  private static final Logger LOG = LogManager.getLogger();

  private static RustKZG instance;
  private LibPeerDASKZG library;
  private boolean initialized;

  static synchronized RustKZG getInstance() {
    if (instance == null) {
      instance = new RustKZG();
    }
    return instance;
  }

  private RustKZG() {}

  @Override
  public synchronized void loadTrustedSetup(final String trustedSetupFile) throws KZGException {
    if (!initialized) {
      try {
        this.library = new LibPeerDASKZG();
        this.initialized = true;
        LOG.info("Loaded LibPeerDASKZG library");
      } catch (final Exception ex) {
        throw new KZGException("Failed to load LibPeerDASKZG Rust library", ex);
      }
    }
  }

  @Override
  public synchronized void freeTrustedSetup() throws KZGException {
    if (!initialized) {
      throw new KZGException("Trusted setup already freed");
    }
    try {
      library.close();
      this.initialized = false;
    } catch (final Exception ex) {
      throw new KZGException("Failed to free trusted setup", ex);
    }
  }

  @Override
  public boolean verifyBlobKzgProof(
      final Bytes blob, final KZGCommitment kzgCommitment, final KZGProof kzgProof)
      throws KZGException {
    throw new RuntimeException("LibPeerDASKZG library doesn't support verifyBlobKzgProof");
  }

  @Override
  public boolean verifyBlobKzgProofBatch(
      final List<Bytes> blobs,
      final List<KZGCommitment> kzgCommitments,
      final List<KZGProof> kzgProofs)
      throws KZGException {
    throw new RuntimeException("LibPeerDASKZG library doesn't support verifyBlobKzgProofBatch");
  }

  @Override
  public KZGCommitment blobToKzgCommitment(final Bytes blob) throws KZGException {
    throw new RuntimeException("LibPeerDASKZG library doesn't support blobToKzgCommitment");
  }

  @Override
  public KZGProof computeBlobKzgProof(final Bytes blob, final KZGCommitment kzgCommitment)
      throws KZGException {
    throw new RuntimeException("LibPeerDASKZG library doesn't support computeBlobKzgProof");
  }

  @Override
  public List<KZGCellAndProof> computeCellsAndProofs(Bytes blob) {
    final CellsAndProofs cellsAndProofs = library.computeCellsAndKZGProofs(blob.toArrayUnsafe());
    final Stream<KZGCell> kzgCellStream =
        Arrays.stream(cellsAndProofs.getCells()).map(Bytes::wrap).map(KZGCell::new);

    final Stream<KZGProof> kzgProofStream =
        Arrays.stream(cellsAndProofs.getProofs()).map(Bytes48::wrap).map(KZGProof::new);

    return Streams.zip(kzgCellStream, kzgProofStream, KZGCellAndProof::new).toList();
  }

  @Override
  public boolean verifyCellProofBatch(
      List<KZGCommitment> commitments,
      List<KZGCellWithIds> cellWithIdsList,
      List<KZGProof> proofs) {
    return library.verifyCellKZGProofBatch(
        commitments.stream().map(KZGCommitment::toArrayUnsafe).toArray(byte[][]::new),
        cellWithIdsList.stream()
            .mapToLong(cellWithIds -> cellWithIds.rowId().id().longValue())
            .toArray(),
        cellWithIdsList.stream()
            .mapToLong(cellWithIds -> cellWithIds.columnId().id().longValue())
            .toArray(),
        cellWithIdsList.stream()
            .map(cellWithIds -> cellWithIds.cell().bytes().toArrayUnsafe())
            .toArray(byte[][]::new),
        proofs.stream().map(KZGProof::toArrayUnsafe).toArray(byte[][]::new));
  }

  @Override
  public List<KZGCellAndProof> recoverCellsAndProofs(List<KZGCellWithColumnId> cells) {
    long[] cellIds = cells.stream().mapToLong(c -> c.columnId().id().longValue()).toArray();
    byte[][] cellBytes =
        cells.stream().map(c -> c.cell().bytes().toArrayUnsafe()).toArray(byte[][]::new);
    final CellsAndProofs cellsAndProofs = library.recoverCellsAndProofs(cellIds, cellBytes);
    final byte[][] recoveredCells = cellsAndProofs.getCells();
    final Stream<KZGCell> kzgCellStream =
        Arrays.stream(recoveredCells).map(Bytes::wrap).map(KZGCell::new);
    final byte[][] recoveredProofs = cellsAndProofs.getProofs();
    final Stream<KZGProof> kzgProofStream =
        Arrays.stream(recoveredProofs).map(Bytes48::wrap).map(KZGProof::new);
    return Streams.zip(kzgCellStream, kzgProofStream, KZGCellAndProof::new).toList();
  }
}
