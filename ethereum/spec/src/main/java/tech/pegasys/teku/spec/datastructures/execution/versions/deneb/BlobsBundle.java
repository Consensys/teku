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

package tech.pegasys.teku.spec.datastructures.execution.versions.deneb;

import com.google.common.base.MoreObjects;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.kzg.KZGCommitment;

public class BlobsBundle {

  private final Bytes32 blockHash;
  private final List<KZGCommitment> kzgs;
  private final List<Blob> blobs;

  public BlobsBundle(
      final Bytes32 blockHash, final List<KZGCommitment> kzgs, final List<Blob> blobs) {
    this.blockHash = blockHash;
    this.kzgs = kzgs;
    this.blobs = blobs;
  }

  public Bytes32 getBlockHash() {
    return blockHash;
  }

  public List<KZGCommitment> getKzgs() {
    return kzgs;
  }

  public List<Blob> getBlobs() {
    return blobs;
  }

  public String toBriefBlobsString() {
    return MoreObjects.toStringHelper(this)
        .add("blockHash", blockHash)
        .add("kzgs", kzgs)
        .add("blobs", blobs.stream().map(Blob::toBriefString).collect(Collectors.toList()))
        .toString();
  }

  /** It's very big, use carefully */
  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("blockHash", blockHash)
        .add("kzgs", kzgs)
        .add("blobs", blobs)
        .toString();
  }
}
