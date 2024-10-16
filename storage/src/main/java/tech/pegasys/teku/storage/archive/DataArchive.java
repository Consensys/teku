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
import java.util.List;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;

/**
 * Interface for a data archive which stores prunable BlobSidecars outside the data availability
 * window and could be extended later to include other data types. It is expected that the
 * DataArchive is on disk or externally stored with slow write and recovery times. Initial interface
 * is write only, but may be expanded to include read operations later.
 */
public interface DataArchive {

  /**
   * Returns the archive writer capable of storing BlobSidecars.
   *
   * @return a closeable DataArchiveWriter for writing BlobSidecars
   * @throws IOException throw exception if it fails to get a writer.
   */
  DataArchiveWriter<List<BlobSidecar>> getBlobSidecarWriter() throws IOException;
}
