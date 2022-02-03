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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import tech.pegasys.teku.validator.client.Validator;

public class PostRemoteKeysRequest {
  private List<Validator> validators = new ArrayList<>();

  public PostRemoteKeysRequest() {}

  public PostRemoteKeysRequest(final List<Validator> validators) {
    this.validators = validators;
  }

  public List<Validator> getValidators() {
    return validators;
  }

  public void setValidators(final List<Validator> validators) {
    this.validators = validators;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final PostRemoteKeysRequest that = (PostRemoteKeysRequest) o;
    return Objects.equals(validators, that.validators);
  }

  @Override
  public int hashCode() {
    return Objects.hash(validators);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("validators", validators).toString();
  }
}
