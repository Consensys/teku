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

package tech.pegasys.teku.ethereum.executionclient.schema;

import static com.google.common.base.Preconditions.checkNotNull;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.MoreObjects;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import tech.pegasys.teku.ethereum.executionclient.serialization.Bytes32Deserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.Bytes48Deserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.BytesDeserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.BytesSerializer;
import tech.pegasys.teku.infrastructure.ssz.collections.impl.SszByteVectorImpl;
import tech.pegasys.teku.kzg.KZGCommitment;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobsBundle;

public class BlobsBundleV1 {
  @JsonSerialize(using = BytesSerializer.class)
  @JsonDeserialize(using = Bytes32Deserializer.class)
  private final Bytes32 blockHash;

  @JsonSerialize(contentUsing = BytesSerializer.class)
  @JsonDeserialize(contentUsing = Bytes48Deserializer.class)
  private final List<Bytes48> kzgs;

  @JsonSerialize(contentUsing = BytesSerializer.class)
  @JsonDeserialize(contentUsing = BytesDeserializer.class)
  private final List<Bytes> blobs;

  public BlobsBundleV1(
      @JsonProperty("blockHash") final Bytes32 blockHash,
      @JsonProperty("kzgs") final List<Bytes48> kzgs,
      @JsonProperty("blobs") final List<Bytes> blobs) {
    checkNotNull(blockHash, "blockHash");
    checkNotNull(kzgs, "kzgs");
    checkNotNull(blobs, "blobs");
    this.blockHash = blockHash;
    this.kzgs = kzgs;
    this.blobs = blobs;
  }

  public static BlobsBundleV1 fromInternalBlobsBundle(final BlobsBundle blobsBundle) {
    return new BlobsBundleV1(
        blobsBundle.getBlockHash(),
        blobsBundle.getKzgs().stream()
            .map(KZGCommitment::getBytesCompressed)
            .collect(Collectors.toList()),
        blobsBundle.getBlobs().stream()
            .map(SszByteVectorImpl::getBytes)
            .collect(Collectors.toList()));
  }

  public BlobsBundle asInternalBlobsBundle(final BlobSchema blobSchema) {
    return new BlobsBundle(
        blockHash,
        kzgs.stream().map(KZGCommitment::new).collect(Collectors.toList()),
        blobs.stream().map(bytes -> new Blob(blobSchema, bytes)).collect(Collectors.toList()));
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final BlobsBundleV1 that = (BlobsBundleV1) o;
    return Objects.equals(blockHash, that.blockHash)
        && Objects.equals(kzgs, that.kzgs)
        && Objects.equals(blobs, that.blobs);
  }

  @Override
  public int hashCode() {
    return Objects.hash(blockHash, kzgs, blobs);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("blockHash", blockHash)
        .add("kzgs", kzgs)
        .add(
            "blobs",
            blobs.stream()
                .map(blob -> blob.slice(0, 7).toUnprefixedHexString())
                .collect(Collectors.toList()))
        .toString();
  }
}
