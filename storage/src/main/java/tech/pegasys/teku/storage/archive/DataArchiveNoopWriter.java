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

package tech.pegasys.teku.storage.archive;

import java.io.IOException;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;

public class DataArchiveNoopWriter<T> implements DataArchiveWriter<T> {

  public static final DataArchiveNoopWriter<BlobSidecar> NOOP_BLOBSIDECAR_STORE =
      new DataArchiveNoopWriter<>();

  @Override
  public boolean archive(final T data) {
    return true;
  }

  @Override
  public void close() throws IOException {}
}