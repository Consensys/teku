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

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.List;
import tech.pegasys.artemis.beaconrestapi.RestApiConstants;
import tech.pegasys.artemis.datastructures.state.Validator;
import tech.pegasys.artemis.datastructures.util.ValidatorsUtil;
import tech.pegasys.artemis.util.SSZTypes.SSZList;
import tech.pegasys.artemis.util.config.Constants;

public class BeaconValidatorsResponse {
  public final List<ValidatorWithIndex> validatorList;
  private long totalSize;
  private int nextPageToken;

  public BeaconValidatorsResponse(SSZList<Validator> sszList) {
    this(sszList.asList());
  }

  public BeaconValidatorsResponse(List<Validator> list) {
    this(
        list,
        false,
        Constants.FAR_FUTURE_EPOCH,
        RestApiConstants.PAGE_SIZE_DEFAULT,
        RestApiConstants.PAGE_TOKEN_DEFAULT);
  }

  public BeaconValidatorsResponse(
      SSZList<Validator> list,
      final boolean activeOnly,
      final UnsignedLong epoch,
      final int pageSize,
      final int pageToken) {
    this(list.asList(), activeOnly, epoch, pageSize, pageToken);
  }

  public BeaconValidatorsResponse(
      final List<Validator> list,
      final boolean activeOnly,
      final UnsignedLong epoch,
      final int pageSize,
      final int pageToken) {

    if (pageSize > 0 && pageToken >= 0) {
      int offset = pageToken * pageSize;
      this.totalSize = getEffectiveListSize(list, activeOnly, epoch);
      if (offset >= list.size()) {
        this.validatorList = List.of();
        this.nextPageToken = 0;
        return;
      }
      validatorList = new ArrayList<>();
      int i = offset;
      int numberAdded = 0;
      while (i < list.size() && numberAdded < pageSize) {
        if (!activeOnly || ValidatorsUtil.is_active_validator(list.get(i), epoch)) {
          validatorList.add(new ValidatorWithIndex(list.get(i), i));
          numberAdded++;
        }
        i++;
      }
      if (totalSize == 0 || offset + numberAdded >= list.size()) {
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

  public long getTotalSize() {
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

  public static long getEffectiveListSize(List<Validator> list, boolean activeOnly, UnsignedLong epoch) {
    if (!activeOnly) {
      return list.size();
    } else {
      return list.stream().filter(v -> ValidatorsUtil.is_active_validator(v, epoch)).count();
      }
  }
}
