/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.services.powchain.execution.client.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

public class NewBlockResponse {

  private Boolean valid;

  public NewBlockResponse(@JsonProperty("valid") Boolean valid) {
    this.valid = valid;
  }

  public Boolean getValid() {
    return valid;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    NewBlockResponse that = (NewBlockResponse) o;
    return Objects.equals(valid, that.valid);
  }

  @Override
  public int hashCode() {
    return Objects.hash(valid);
  }

  @Override
  public String toString() {
    return "NewBlockResponse{" + "valid=" + valid + '}';
  }
}
