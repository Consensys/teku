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

package tech.pegasys.teku.beacon.sync.forward.singlepeer;

import tech.pegasys.teku.networking.eth2.rpc.core.InvalidResponseException;
import tech.pegasys.teku.spec.datastructures.execution.versions.deneb.BlobSidecar;

public class FailedBlobSidecarImportException extends InvalidResponseException {

  public FailedBlobSidecarImportException(final String message) {
    super(message);
  }

  public FailedBlobSidecarImportException(final BlobSidecar blobSidecar, final Throwable cause) {
    super(
        String.format(
            "Unable to import blob sidecar for slot %s due to an error: %s",
            blobSidecar.getSlot(), cause));
  }
}
