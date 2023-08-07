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

package tech.pegasys.teku.kzg.ckzg4844;

import static tech.pegasys.teku.kzg.ckzg4844.CKZG4844.G1_POINT_SIZE;
import static tech.pegasys.teku.kzg.ckzg4844.CKZG4844.G2_POINT_SIZE;

import ethereum.ckzg4844.CKZG4844JNI;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.http.UrlSanitizer;
import tech.pegasys.teku.infrastructure.io.resource.ResourceLoader;
import tech.pegasys.teku.kzg.KZGCommitment;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.kzg.TrustedSetup;

public class CKZG4844Utils {

  public static byte[] flattenBytes(final Stream<Bytes> bytesStream, final int capacity) {
    return bytesStream.reduce(Bytes::concatenate).map(Bytes::toArray).orElse(new byte[capacity]);
  }

  public static byte[] flattenBlobs(final List<Bytes> blobs) {
    return flattenBytes(blobs.stream(), CKZG4844JNI.getBytesPerBlob() * blobs.size());
  }

  public static byte[] flattenCommitments(final List<KZGCommitment> commitments) {
    final Stream<Bytes> commitmentsBytes =
        commitments.stream().map(KZGCommitment::getBytesCompressed);
    return flattenBytes(commitmentsBytes, KZGCommitment.KZG_COMMITMENT_SIZE * commitments.size());
  }

  public static byte[] flattenProofs(final List<KZGProof> kzgProofs) {
    final Stream<Bytes> kzgProofBytes = kzgProofs.stream().map(KZGProof::getBytesCompressed);
    return flattenBytes(kzgProofBytes, KZGProof.KZG_PROOF_SIZE * kzgProofs.size());
  }

  public static TrustedSetup parseTrustedSetupFile(final String filePath) throws IOException {
    final String sanitizedTrustedSetup = UrlSanitizer.sanitizePotentialUrl(filePath);
    final InputStream resource =
        ResourceLoader.urlOrFile("application/octet-stream")
            .load(filePath)
            .orElseThrow(() -> new FileNotFoundException(sanitizedTrustedSetup + " is not found"));
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(resource, StandardCharsets.UTF_8))) {
      // txt format :
      // Number of G1 points
      final int g1Size = Integer.parseInt(reader.readLine());
      // Number of G2 points
      final int g2Size = Integer.parseInt(reader.readLine());
      // List of G1 points, one on each new line
      final List<Bytes> g1Points = new ArrayList<>();
      for (int i = 0; i < g1Size; i++) {
        final Bytes g1Point = Bytes.fromHexString(reader.readLine(), G1_POINT_SIZE);
        g1Points.add(g1Point);
      }
      // List of G2 points, one on each new line
      final List<Bytes> g2Points = new ArrayList<>();
      for (int i = 0; i < g2Size; i++) {
        final Bytes g2Point = Bytes.fromHexString(reader.readLine(), G2_POINT_SIZE);
        g2Points.add(g2Point);
      }

      return new TrustedSetup(g1Points, g2Points);
    } catch (Exception ex) {
      throw new IOException(String.format("Failed to parse trusted setup file\n: %s", filePath));
    }
  }
}
