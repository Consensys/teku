/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.spec.datastructures.operations;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.schema.SszContainerSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.operations.versions.gloas.AttestationDataGloasSchema;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;

public interface AttestationDataSchema<T extends AttestationData> extends SszContainerSchema<T> {

  AttestationData create(
      final UInt64 slot,
      final UInt64 index,
      final Bytes32 beaconBlockRoot,
      final Checkpoint source,
      final Checkpoint target);

  default Optional<AttestationDataGloasSchema> toVersionGloas() {
    return Optional.empty();
  }

  @SuppressWarnings("unchecked")
  default AttestationDataSchema<AttestationData> castTypeToAttestationSchema() {
    return (AttestationDataSchema<AttestationData>) this;
  }
}
