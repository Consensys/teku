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

package tech.pegasys.artemis.beaconrestapi.schema;

import tech.pegasys.artemis.datastructures.state.Validator;
import tech.pegasys.artemis.util.SSZTypes.SSZList;

public class BeaconValidatorsResponse {
  public final SSZList<Validator> validatorList;
  private int totalSize;

  public BeaconValidatorsResponse(SSZList<Validator> validatorList) {
    this(validatorList, 20, 0);
  }

  public BeaconValidatorsResponse(
      SSZList<Validator> validatorList, final int pageSize, final int pageToken) {
    // first page is pageToken = 0
    if (pageSize > 0 && pageToken >= 0) {
      int offset = pageToken * pageSize;
      SSZList<Validator> pageOfValidators = new SSZList<>(Validator.class, pageSize);
      this.totalSize = validatorList.size();
      // if the offset is outside the bounds, just return the list as is
      if (offset >= validatorList.size()) {
        this.validatorList = validatorList;
        return;
      }
      // otherwise get a page of results
      for (int i = offset; i < offset + pageSize; i++) {
        pageOfValidators.add(validatorList.get(offset));
      }
      this.validatorList = pageOfValidators;
    } else {
      // TODO IllegalArgumentException ?
      this.validatorList = validatorList;
      this.totalSize = validatorList.size();
    }
  }

  public int getTotalSize() {
    return totalSize;
  }
}
