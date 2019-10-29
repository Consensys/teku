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

package org.ethereum.beacon.core.operations;

import com.google.common.base.Objects;
import java.util.function.Function;
import javax.annotation.Nullable;
import org.ethereum.beacon.core.BeaconBlockHeader;
import org.ethereum.beacon.core.spec.SpecConstants;
import org.ethereum.beacon.core.types.Hashable;
import org.ethereum.beacon.core.types.ValidatorIndex;
import org.ethereum.beacon.ssz.annotation.SSZ;
import org.ethereum.beacon.ssz.annotation.SSZSerializable;
import tech.pegasys.artemis.ethereum.core.Hash32;

@SSZSerializable
public class ProposerSlashing {
  @SSZ private final ValidatorIndex proposerIndex;
  @SSZ private final BeaconBlockHeader header1;
  @SSZ private final BeaconBlockHeader header2;

  public ProposerSlashing(
      ValidatorIndex proposerIndex, BeaconBlockHeader header1, BeaconBlockHeader header2) {
    this.proposerIndex = proposerIndex;
    this.header1 = header1;
    this.header2 = header2;
  }

  public ValidatorIndex getProposerIndex() {
    return proposerIndex;
  }

  public BeaconBlockHeader getHeader1() {
    return header1;
  }

  public BeaconBlockHeader getHeader2() {
    return header2;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ProposerSlashing that = (ProposerSlashing) o;
    return Objects.equal(proposerIndex, that.proposerIndex)
        && Objects.equal(header1, that.header1)
        && Objects.equal(header2, that.header2);
  }

  @Override
  public int hashCode() {
    int result = proposerIndex.hashCode();
    result = 31 * result + header1.hashCode();
    result = 31 * result + header2.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return toString(null, null);
  }

  public String toString(
      @Nullable SpecConstants spec, @Nullable Function<? super Hashable<Hash32>, Hash32> hasher) {
    return "ProposerSlashing["
        + "proposer: "
        + proposerIndex
        + ", header1: "
        + header1.toStringFull(spec, hasher)
        + ", header2: "
        + header1.toStringFull(spec, hasher)
        + "]";
  }
}
