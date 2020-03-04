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

import java.util.ArrayList;
import java.util.List;
import tech.pegasys.artemis.datastructures.state.Validator;

public class BeaconValidatorsResponse {
  public final List<ValidatorWithIndex> validatorList;
  private int totalSize;
  private int nextPageToken;

  public BeaconValidatorsResponse(List<Validator> list) {
    this(list, 20, 0);
  }

  public BeaconValidatorsResponse(
      final List<Validator> list, final int pageSize, final int pageToken) {
    if (pageSize > 0 && pageToken >= 0) {
      int offset = pageToken * pageSize;
      this.totalSize = list.size();
      if (offset >= list.size()) {
        this.validatorList = List.of();
        this.nextPageToken = 0;
        return;
      }
      validatorList = new ArrayList<>();
      for (int i = offset; i < Math.min(offset + pageSize, list.size()); i++) {
        validatorList.add(new ValidatorWithIndex(list.get(i), i));
      }
      if (totalSize == 0 || offset + pageSize >= list.size()) {
        this.nextPageToken = 0;
      } else {
        this.nextPageToken = pageToken + 1;
      }
    } else {
      this.validatorList = List.of();
      this.totalSize = list.size();
      this.nextPageToken = 0;
    }
  }

  public int getTotalSize() {
    return totalSize;
  }

  public int getNextPageToken() {
    return nextPageToken;
  }

  public static class ValidatorWithIndex {
    public Validator validator;
    public int index;

    public ValidatorWithIndex(Validator validator, int index) {
      this.validator = validator;
      this.index = index;
    }
  }
}
