/*
 * Copyright 2022 ConsenSys AG.
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

package tech.pegasys.teku.validator.client.restapi.apis.schema;

import com.google.common.base.MoreObjects;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import tech.pegasys.teku.bls.BLSPublicKey;

public class PostRemoteKeysRequest {
  private List<BLSPublicKey> pubKeys = new ArrayList<>();
  private List<URL> signers = new ArrayList<>();
  private Optional<String> slashingProtection = Optional.empty();

  public PostRemoteKeysRequest() {}

  public PostRemoteKeysRequest(
      final List<BLSPublicKey> pubKeys,
      final List<URL> signers,
      final Optional<String> slashingProtection) {
    this.pubKeys = pubKeys;
    this.signers = signers;
    this.slashingProtection = slashingProtection;
  }

  public List<BLSPublicKey> getPubKeys() {
    return pubKeys;
  }

  public void setPubKeys(final List<BLSPublicKey> pubKeys) {
    this.pubKeys = pubKeys;
  }

  public List<URL> getSigners() {
    return signers;
  }

  public void setSigners(final List<URL> signers) {
    this.signers = signers;
  }

  public Optional<String> getSlashingProtection() {
    return slashingProtection;
  }

  public void setSlashingProtection(final Optional<String> slashingProtection) {
    this.slashingProtection = slashingProtection;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final PostRemoteKeysRequest that = (PostRemoteKeysRequest) o;
    return Objects.equals(pubKeys, that.pubKeys)
        && Objects.equals(signers, that.signers)
        && Objects.equals(slashingProtection, that.slashingProtection);
  }

  @Override
  public int hashCode() {
    return Objects.hash(pubKeys, signers, slashingProtection);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("pubKeys", pubKeys)
        .add("signers", signers)
        .add("slashingProtection", slashingProtection)
        .toString();
  }
}
