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

package tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods;

import static tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.DataColumnSidecarsResponseInvalidResponseException.InvalidResponseType;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;

public abstract class AbstractDataColumnSidecarValidator {

  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final KZG kzg;
  final Peer peer;

  public AbstractDataColumnSidecarValidator(final Peer peer, final Spec spec, final KZG kzg) {
    this.peer = peer;
    this.spec = spec;
    this.kzg = kzg;
  }

  void verifyKzgProof(final DataColumnSidecar dataColumnSidecar) {
    if (!verifyDataColumnSidecarKzgProof(dataColumnSidecar)) {
      throw new DataColumnSidecarsResponseInvalidResponseException(
          peer, InvalidResponseType.DATA_COLUMN_SIDECAR_KZG_VERIFICATION_FAILED);
    }
  }

  private boolean verifyDataColumnSidecarKzgProof(final DataColumnSidecar dataColumnSidecar) {
    try {
      return spec.atSlot(dataColumnSidecar.getSlot())
          .miscHelpers()
          .verifyDataColumnSidecarKzgProof(kzg, dataColumnSidecar);
    } catch (final Exception ex) {
      LOG.debug(
          "KZG verification failed for DataColumnSidecar {}", dataColumnSidecar.toLogString());
      throw new DataColumnSidecarsResponseInvalidResponseException(
          peer, InvalidResponseType.DATA_COLUMN_SIDECAR_KZG_VERIFICATION_FAILED, ex);
    }
  }
}
