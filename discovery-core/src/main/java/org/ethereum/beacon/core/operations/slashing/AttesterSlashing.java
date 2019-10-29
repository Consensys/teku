/*
 * Copyright 2019 ConsenSys AG.
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

package org.ethereum.beacon.core.operations.slashing;

import org.ethereum.beacon.ssz.annotation.SSZ;
import org.ethereum.beacon.ssz.annotation.SSZSerializable;

@SSZSerializable
public class AttesterSlashing {
  @SSZ private final IndexedAttestation attestation1;
  @SSZ private final IndexedAttestation attestation2;

  public AttesterSlashing(IndexedAttestation attestation1, IndexedAttestation attestation2) {
    this.attestation1 = attestation1;
    this.attestation2 = attestation2;
  }

  public IndexedAttestation getAttestation1() {
    return attestation1;
  }

  public IndexedAttestation getAttestation2() {
    return attestation2;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    AttesterSlashing that = (AttesterSlashing) o;
    if (!attestation1.equals(that.attestation1)) {
      return false;
    }
    return attestation2.equals(that.attestation2);
  }

  @Override
  public int hashCode() {
    int result = attestation1.hashCode();
    result = 31 * result + attestation2.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return "AttesterSlashing[" + "att1=" + attestation1 + "att2=" + attestation2 + "]";
  }
}
