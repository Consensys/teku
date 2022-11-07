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

public interface SpecConfigEip4844 extends SpecConfigCapella {
  Bytes BLOB_TX_TYPE = Bytes.fromHexString("0x05");
  Bytes VERSIONED_HASH_VERSION_KZG = Bytes.fromHexString("0x01");

  static SpecConfigEip4844 required(SpecConfig specConfig) {
    return specConfig
        .toVersionEip4844()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected EIP-4844 spec config but got: "
                        + specConfig.getClass().getSimpleName()));
  }

  @Override
  Optional<SpecConfigEip4844> toVersionEip4844();
}
