/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.api.schema;

import static tech.pegasys.teku.util.config.Constants.MAX_VALIDATORS_PER_COMMITTEE;

import com.google.common.primitives.UnsignedLong;
import java.util.List;
import java.util.stream.Collectors;
import tech.pegasys.teku.ssz.SSZTypes.SSZList;

public class IndexedAttestation {
  public final List<UnsignedLong> attesting_indices;
  public final AttestationData data;
  public final BLSSignature signature;

  public IndexedAttestation(
      tech.pegasys.teku.datastructures.operations.IndexedAttestation indexedAttestation) {
    this.attesting_indices =
        indexedAttestation.getAttesting_indices().stream().collect(Collectors.toList());
    this.data = new AttestationData(indexedAttestation.getData());
    this.signature = new BLSSignature(indexedAttestation.getSignature());
  }

  public tech.pegasys.teku.datastructures.operations.IndexedAttestation
      asInternalIndexedAttestation() {
    return new tech.pegasys.teku.datastructures.operations.IndexedAttestation(
        SSZList.createMutable(attesting_indices, MAX_VALIDATORS_PER_COMMITTEE, UnsignedLong.class),
        data.asInternalAttestationData(),
        signature.asInternalBLSSignature());
  }
}
