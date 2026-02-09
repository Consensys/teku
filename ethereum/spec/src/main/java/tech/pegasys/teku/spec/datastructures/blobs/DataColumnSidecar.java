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

package tech.pegasys.teku.spec.datastructures.blobs;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.logging.LogFormatter;
import tech.pegasys.teku.infrastructure.ssz.SszContainer;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumn;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProof;

public interface DataColumnSidecar extends SszContainer {

  UInt64 getIndex();

  DataColumn getColumn();

  // TODO-GLOAS: https://github.com/Consensys/teku/issues/10311 need to verify this is not called
  // for Gloas or make it an Optional
  SszList<SszKZGCommitment> getKzgCommitments();

  SszList<SszKZGProof> getKzgProofs();

  UInt64 getSlot();

  Bytes32 getBeaconBlockRoot();

  Optional<SignedBeaconBlockHeader> getMaybeSignedBlockHeader();

  default SlotAndBlockRoot getSlotAndBlockRoot() {
    return new SlotAndBlockRoot(getSlot(), getBeaconBlockRoot());
  }

  default String toLogString() {
    return LogFormatter.formatDataColumnSidecar(
        getSlot(),
        getBeaconBlockRoot(),
        getIndex(),
        getColumn().toBriefString(),
        getKzgCommitments().size(),
        getKzgProofs().size());
  }
}
