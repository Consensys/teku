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

package tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc;

import static tech.pegasys.teku.spec.config.Constants.MAX_REQUEST_BLOB_SIDECARS;

import java.util.List;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.impl.SszListImpl;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;

public class BlobSidecarsByRootRequestMessage extends SszListImpl<BlobIdentifier>
    implements SszList<BlobIdentifier>, RpcRequest {

  public static class BlobSidecarsByRootRequestMessageSchema
      extends AbstractSszListSchema<BlobIdentifier, BlobSidecarsByRootRequestMessage> {

    public BlobSidecarsByRootRequestMessageSchema(final SpecConfigDeneb specConfigDeneb) {
      super(
          BlobIdentifier.SSZ_SCHEMA,
          MAX_REQUEST_BLOB_SIDECARS.times(specConfigDeneb.getMaxBlobsPerBlock()).longValue());
    }

    @Override
    public BlobSidecarsByRootRequestMessage createFromBackingNode(final TreeNode node) {
      return new BlobSidecarsByRootRequestMessage(this, node);
    }
  }

  public BlobSidecarsByRootRequestMessage(
      final BlobSidecarsByRootRequestMessageSchema schema,
      final List<BlobIdentifier> blobIdentifiers) {
    super(schema, schema.createTreeFromElements(blobIdentifiers));
  }

  private BlobSidecarsByRootRequestMessage(
      final BlobSidecarsByRootRequestMessageSchema schema, final TreeNode node) {
    super(schema, node);
  }

  @Override
  public int getMaximumRequestChunks() {
    return size();
  }

  @Override
  public String toString() {
    return "BlobSidecarsByRootRequestMessage{" + super.toString() + "}";
  }
}
