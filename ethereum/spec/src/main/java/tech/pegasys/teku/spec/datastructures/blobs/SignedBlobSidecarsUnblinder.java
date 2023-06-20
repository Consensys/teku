/*
 * Copyright ConsenSys Software Inc., 2023
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

package tech.pegasys.teku.spec.datastructures.blobs;

import java.util.List;
import java.util.function.Supplier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.SignedBlobSidecar;
import tech.pegasys.teku.spec.datastructures.builder.BlobsBundle;

/**
 * Classes implementing this interface MUST:
 *
 * <p>- expect {@link #setBlobsBundleSupplier ( Supplier)} to be called, which provides a future of
 * a BlobsBundle consistent with the blinded blob sidecars
 *
 * <p>- expect the {@link #unblind()} method to be called after {@link #setBlobsBundleSupplier (
 * Supplier)}.
 *
 * <p>- unblind() has now all the information (blobs bundle + blinded blob sidecars) to construct
 * the unblinded version of the blob sidecars
 */
public interface SignedBlobSidecarsUnblinder {

  void setBlobsBundleSupplier(Supplier<SafeFuture<BlobsBundle>> blobsBundleSupplier);

  SafeFuture<List<SignedBlobSidecar>> unblind();
}
