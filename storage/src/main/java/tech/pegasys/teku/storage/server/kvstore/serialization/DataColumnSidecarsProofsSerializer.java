/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.storage.server.kvstore.serialization;

import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes48;
import org.apache.tuweni.ssz.SSZ;
import tech.pegasys.teku.kzg.KZGProof;

public class DataColumnSidecarsProofsSerializer implements KvStoreSerializer<List<List<KZGProof>>> {
  @Override
  public List<List<KZGProof>> deserialize(final byte[] data) {
    return SSZ.decode(
        Bytes.of(data),
        reader -> {
          final List<List<KZGProof>> output = new ArrayList<>();
          final List<KZGProof> currentProofs = new ArrayList<>();
          final int blobSize = reader.readInt32();

          while (!reader.isComplete()) {
            final KZGProof kzgProof =
                KZGProof.fromBytesCompressed(Bytes48.wrap(reader.readFixedBytes(KZGProof.SIZE)));
            currentProofs.add(kzgProof);
            if (currentProofs.size() == blobSize) {
              output.add(new ArrayList<>(currentProofs));
              currentProofs.clear();
            }
          }

          if (!currentProofs.isEmpty()) {
            throw new RuntimeException(
                String.format(
                    "Unexpected proofs found in database. Expecting %d blobs, deserialized %s columns",
                    blobSize, output.size()));
          }
          return output;
        });
  }

  @Override
  public byte[] serialize(final List<List<KZGProof>> value) {
    Bytes bytes =
        SSZ.encode(
            writer -> {
              final int blobSize = value.isEmpty() ? 0 : value.getFirst().size();
              writer.writeInt32(blobSize);
              value.forEach(
                  column ->
                      column.forEach(
                          kzgProof -> writer.writeFixedBytes(kzgProof.getBytesCompressed())));
            });
    return bytes.toArrayUnsafe();
  }
}
