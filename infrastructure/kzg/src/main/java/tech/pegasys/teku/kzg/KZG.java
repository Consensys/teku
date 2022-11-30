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

package tech.pegasys.teku.kzg;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.io.resource.ResourceLoader;
import tech.pegasys.teku.kzg.impl.KZG4844;
import tech.pegasys.teku.kzg.impl.KzgException;
import tech.pegasys.teku.kzg.impl.ckzg.CkzgLoader;

/**
 * Implements the standard KZG functions needed for the EIP-4844 specification.
 *
 * <p>This package strives to implement the KZG standard as used in the Eth2 specification and is
 * the entry-point for all KZG operations in Teku. Do not rely on any of the classes used by this
 * one conforming to the specification or standard.
 */
public final class KZG {
  private static final Logger LOG = LogManager.getLogger();
  private static final String FILE_SCHEME = "file";

  private static KZG4844 kzgImpl;

  static {
    resetKzgImplementation();
  }

  public static void setKzgImplementation(final KZG4844 kzgImpl) {
    KZG.kzgImpl = kzgImpl;
  }

  public static void resetKzgImplementation() {
    if (CkzgLoader.INSTANCE.isPresent()) {
      kzgImpl = CkzgLoader.INSTANCE.get();
      LOG.info("KZG: loaded CKZG library");
    } else {
      throw new KzgException("Failed to load CKZG library.");
    }
  }

  public static KZG4844 getKzgImpl() {
    return kzgImpl;
  }

  public static void resetTrustedSetup() {
    try {
      kzgImpl.resetTrustedSetup();
    } catch (final KzgException ex) {
      LOG.trace("Trying to reset KZG trusted setup which is not loaded");
    }
  }

  public static void loadTrustedSetup(final URL url) {
    final String filePath;
    try {
      filePath = copyResourceToTempFileIfNeeded(url);
    } catch (final IOException ex) {
      throw new KzgException(
          String.format("Failed to copy trusted setup '%s' to temporary file", url), ex);
    }
    kzgImpl.loadTrustedSetup(filePath);
  }

  public static KZGProof computeAggregateKzgProof(final List<Bytes> blobs) {
    return kzgImpl.computeAggregateKzgProof(blobs);
  }

  public static boolean verifyAggregateKzgProof(
      final List<Bytes> blobs, final List<KZGCommitment> kzgCommitments, final KZGProof kzgProof) {
    return kzgImpl.verifyAggregateKzgProof(blobs, kzgCommitments, kzgProof);
  }

  public static KZGCommitment blobToKzgCommitment(final Bytes blob) {
    return kzgImpl.blobToKzgCommitment(blob);
  }

  public static boolean verifyKzgProof(
      final KZGCommitment kzgCommitment,
      final Bytes32 z,
      final Bytes32 y,
      final KZGProof kzgProof) {
    return kzgImpl.verifyKzgProof(kzgCommitment, z, y, kzgProof);
  }

  private static String copyResourceToTempFileIfNeeded(final URL url) throws IOException {
    try {
      if (url.toURI().getScheme().equals(FILE_SCHEME)) {
        return url.getPath();
      }
    } catch (final URISyntaxException ex) {
      throw new KzgException(String.format("%s is incorrect file path", url), ex);
    }

    final Bytes resource =
        ResourceLoader.urlOrFile("application/octet-stream")
            .loadBytes(url.toExternalForm())
            .orElseThrow(() -> new FileNotFoundException("Not found"));

    File temp = File.createTempFile("resource", ".tmp");
    temp.deleteOnExit();

    try (final FileOutputStream out = new FileOutputStream(temp)) {
      out.write(resource.toArray());
    }

    return temp.getAbsolutePath();
  }
}
