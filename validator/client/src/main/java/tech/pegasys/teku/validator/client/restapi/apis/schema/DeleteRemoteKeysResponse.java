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

package tech.pegasys.teku.validator.client.restapi.apis.schema;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import java.util.List;
import java.util.Objects;

public class DeleteRemoteKeysResponse {
  private final List<DeleteKeyResult> data;

  public DeleteRemoteKeysResponse(final List<DeleteKeyResult> data) {
    Preconditions.checkNotNull(data);
    this.data = data;
  }

  public List<DeleteKeyResult> getData() {
    return data;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DeleteRemoteKeysResponse that = (DeleteRemoteKeysResponse) o;
    return Objects.equals(data, that.data);
  }

  @Override
  public int hashCode() {
    return Objects.hash(data);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("data", data).toString();
  }
}
