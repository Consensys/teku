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

package tech.pegasys.artemis.api.schema;

import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.artemis.util.config.Constants.FAR_FUTURE_EPOCH;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class BeaconValidators {
  public static final int PAGE_SIZE_DEFAULT = 250;
  public static final int PAGE_TOKEN_DEFAULT = 0;
  public final List<ValidatorWithIndex> validatorList;
  public final Long totalSize;
  public final Integer nextPageToken;

  @VisibleForTesting
  public BeaconValidators(tech.pegasys.artemis.datastructures.state.BeaconState state) {
    this(state, false, FAR_FUTURE_EPOCH, PAGE_SIZE_DEFAULT, PAGE_TOKEN_DEFAULT);
  }

  @VisibleForTesting
  public BeaconValidators() {
    this.totalSize = 0L;
    this.nextPageToken = 0;
    this.validatorList = List.of();
  }

  @VisibleForTesting
  public BeaconValidators(
      tech.pegasys.artemis.datastructures.state.BeaconState state,
      final boolean activeOnly,
      final UnsignedLong epoch,
      final int pageSize,
      final int pageToken) {
    this(
        state.getValidators().stream().map(Validator::new).collect(Collectors.toList()),
        state.getBalances().stream().collect(Collectors.toList()),
        activeOnly,
        epoch,
        pageSize,
        pageToken);
  }

  public BeaconValidators(BeaconState state, List<BLSPubKey> filter) {
    //    filter.stream().map(pubkey -> {
    //
    //    });
    this.validatorList =
        state.validators.stream()
            .filter(validator -> filter.contains(validator.pubkey))
            .map(validator -> new ValidatorWithIndex(validator, state))
            .collect(Collectors.toList());
    this.totalSize = null;
    this.nextPageToken = null;
  }

  public BeaconValidators(
      final BeaconState state, final boolean activeOnly, final int pageSize, final int pageToken) {
    this(
        state.validators,
        state.balances,
        activeOnly,
        compute_epoch_at_slot(state.slot),
        pageSize,
        pageToken);
  }

  BeaconValidators(
      final List<Validator> list,
      final List<UnsignedLong> balances,
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
        if (!activeOnly || is_active_validator(list.get(i), epoch)) {
          validatorList.add(new ValidatorWithIndex(list.get(i), i, balances.get(i)));
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
      this.totalSize = (long) list.size();
      this.nextPageToken = 0;
    }
  }

  public static class ValidatorWithIndex {
    public final Validator validator;
    public final int index;
    public final UnsignedLong balance;

    public ValidatorWithIndex(
        final Validator validator, final int index, final UnsignedLong balance) {
      this.validator = validator;
      this.index = index;
      this.balance = balance;
    }

    public ValidatorWithIndex(final Validator validator, BeaconState state) {
      this.index = state.validators.indexOf(validator);
      this.validator = validator;
      this.balance = state.balances.get(this.index);
    }
  }

  public static long getEffectiveListSize(
      List<Validator> list, boolean activeOnly, UnsignedLong epoch) {
    if (!activeOnly) {
      return list.size();
    } else {
      return list.stream().filter(v -> is_active_validator(v, epoch)).count();
    }
  }

  private static boolean is_active_validator(Validator validator, UnsignedLong epoch) {
    return validator.activation_epoch.compareTo(epoch) <= 0
        && epoch.compareTo(validator.exit_epoch) < 0;
  }
}
