/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.storage.server;

import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;

public class DatabaseArchiveNoopWriter<T> implements DatabaseArchiveWriter<T> {

  public static final DatabaseArchiveNoopWriter<BlobSidecar> NOOP_BLOBSIDECAR_STORE =
      new DatabaseArchiveNoopWriter<>();

  @Override
  public boolean archive(final T data) {
    return true;
  }
}
