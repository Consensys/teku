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

package tech.pegasys.teku.spec.config;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public interface SpecConfigEip7594 extends SpecConfigDeneb, NetworkingSpecConfigEip7594 {

  static SpecConfigEip7594 required(final SpecConfig specConfig) {
    return specConfig
        .toVersionEip7594()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected EIP7594 spec config but got: "
                        + specConfig.getClass().getSimpleName()));
  }

  Bytes4 getEip7594ForkVersion();

  UInt64 getEip7594ForkEpoch();

  UInt64 getFieldElementsPerCell();

  UInt64 getFieldElementsPerExtBlob();

  /** DataColumnSidecar's */
  UInt64 getKzgCommitmentsInclusionProofDepth();

  int getNumberOfColumns();

  @Override
  Optional<SpecConfigEip7594> toVersionEip7594();
}
