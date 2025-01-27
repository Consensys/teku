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

public interface SpecConfigEip7805 extends SpecConfigFulu {

  static SpecConfigEip7805 required(final SpecConfig specConfig) {
    return specConfig
        .toVersionEip7805()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected Eip7805 spec config but got: "
                        + specConfig.getClass().getSimpleName()));
  }

  Bytes4 getEip7805ForkVersion();

  UInt64 getEip7805ForkEpoch();

  int getInclusionListCommitteeSize();

  int getMaxTransactionsPerInclusionList();

  int getMaxRequestInclusionList();

  int getMaxBytesPerInclusionList();

  @Override
  Optional<SpecConfigEip7805> toVersionEip7805();
}
