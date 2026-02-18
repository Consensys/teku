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

package tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods;

import static tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.DataColumnSidecarsResponseInvalidResponseException.InvalidResponseType;

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.eth2.peers.DataColumnSidecarSignatureValidator;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.logic.common.util.DataColumnSidecarUtil;
import tech.pegasys.teku.spec.logic.common.util.DataColumnSidecarValidationError;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

public abstract class AbstractDataColumnSidecarValidator {

  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final DataColumnSidecarSignatureValidator dataColumnSidecarSignatureValidator;
  final Peer peer;
  final CombinedChainDataClient combinedChainDataClient;

  public AbstractDataColumnSidecarValidator(
      final Peer peer,
      final Spec spec,
      final DataColumnSidecarSignatureValidator dataColumnSidecarSignatureValidator,
      final CombinedChainDataClient combinedChainDataClient) {
    this.peer = peer;
    this.spec = spec;
    this.dataColumnSidecarSignatureValidator = dataColumnSidecarSignatureValidator;
    this.combinedChainDataClient = combinedChainDataClient;
  }

  SafeFuture<Optional<DataColumnSidecarValidationError>> verifyValidity(
      final DataColumnSidecar dataColumnSidecar) {
    try {
      final DataColumnSidecarUtil dataColumnSidecarUtil =
          spec.getDataColumnSidecarUtil(dataColumnSidecar.getSlot());
      if (!dataColumnSidecarUtil.verifyDataColumnSidecarStructure(dataColumnSidecar)) {
        return SafeFuture.completedFuture(
            Optional.of(
                DataColumnSidecarValidationError.Critical.format("Invalid DataColumnSidecar")));
      }
      return dataColumnSidecarUtil.validateWithBlock(
          dataColumnSidecar, combinedChainDataClient::getBlockByBlockRoot);
    } catch (final Exception ex) {
      LOG.debug("Validity check failed for DataColumnSidecar {}", dataColumnSidecar.toLogString());
      return SafeFuture.completedFuture(
          Optional.of(
              DataColumnSidecarValidationError.Critical.format("Invalid DataColumnSidecar")));
    }
  }

  void verifyKzgProof(final DataColumnSidecar dataColumnSidecar) {
    if (!verifyDataColumnSidecarKzgProofs(dataColumnSidecar)) {
      throw new DataColumnSidecarsResponseInvalidResponseException(
          peer, InvalidResponseType.DATA_COLUMN_SIDECAR_KZG_VERIFICATION_FAILED);
    }
  }

  private boolean verifyDataColumnSidecarKzgProofs(final DataColumnSidecar dataColumnSidecar) {
    try {
      return spec.getDataColumnSidecarUtil(dataColumnSidecar.getSlot())
          .verifyDataColumnSidecarKzgProofs(dataColumnSidecar);
    } catch (final Exception ex) {
      LOG.debug(
          "KZG verification failed for DataColumnSidecar {}", dataColumnSidecar.toLogString());
      throw new DataColumnSidecarsResponseInvalidResponseException(
          peer, InvalidResponseType.DATA_COLUMN_SIDECAR_KZG_VERIFICATION_FAILED, ex);
    }
  }

  void verifyInclusionProof(final DataColumnSidecar dataColumnSidecar) {
    if (!verifyDataColumnSidecarInclusionProof(dataColumnSidecar)) {
      throw new DataColumnSidecarsResponseInvalidResponseException(
          peer, InvalidResponseType.DATA_COLUMN_SIDECAR_INCLUSION_PROOF_VERIFICATION_FAILED);
    }
  }

  public SafeFuture<Boolean> verifySignature(final DataColumnSidecar dataColumnSidecar) {
    return dataColumnSidecarSignatureValidator.validateSignature(dataColumnSidecar);
  }

  private boolean verifyDataColumnSidecarInclusionProof(final DataColumnSidecar dataColumnSidecar) {
    try {
      return spec.getDataColumnSidecarUtil(dataColumnSidecar.getSlot())
          .verifyInclusionProof(dataColumnSidecar);
    } catch (final Exception ex) {
      LOG.debug(
          "Inclusion proof verification failed for DataColumnSidecar {}",
          dataColumnSidecar.toLogString());
      throw new DataColumnSidecarsResponseInvalidResponseException(
          peer, InvalidResponseType.DATA_COLUMN_SIDECAR_INCLUSION_PROOF_VERIFICATION_FAILED, ex);
    }
  }
}
