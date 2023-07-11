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

package tech.pegasys.teku.spec.config;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public interface SpecConfigDeneb extends SpecConfigCapella, NetworkingSpecConfigDeneb {
  Bytes VERSIONED_HASH_VERSION_KZG = Bytes.fromHexString("0x01");
  UInt64 BYTES_PER_FIELD_ELEMENT = UInt64.valueOf(32);

  static SpecConfigDeneb required(final SpecConfig specConfig) {
    return specConfig
        .toVersionDeneb()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected Deneb spec config but got: "
                        + specConfig.getClass().getSimpleName()));
  }

  Bytes4 getDenebForkVersion();

  UInt64 getDenebForkEpoch();

  int getFieldElementsPerBlob();

  /**
   * This is set to a higher number than `maxBlobsPerBlock` to use only for ssz list limit. The
   * purpose is to make merkle proofs constructed today valid in the future when `maxBlobsPerBlock`
   * is increased. The limit of the number of blobs and so on commitments is still {@link
   * #getMaxBlobsPerBlock()}
   */
  int getMaxBlobCommitmentsPerBlock();

  int getMaxBlobsPerBlock();

  Optional<String> getTrustedSetupPath();

  boolean isKZGNoop();

  @Override
  Optional<SpecConfigDeneb> toVersionDeneb();
}
