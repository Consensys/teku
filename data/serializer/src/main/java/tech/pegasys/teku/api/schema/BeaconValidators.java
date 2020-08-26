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

package tech.pegasys.teku.api.schema;

import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;
import static tech.pegasys.teku.util.config.Constants.FAR_FUTURE_EPOCH;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class BeaconValidators {
  public static final int PAGE_SIZE_DEFAULT = 250;
  public static final int PAGE_TOKEN_DEFAULT = 0;
  public final List<ValidatorWithIndex> validators;
  public final Long total_size;
  public final Integer next_page_token;

  public static BeaconValidators emptySet() {
    return new BeaconValidators();
  }

  @VisibleForTesting
  public BeaconValidators(tech.pegasys.teku.datastructures.state.BeaconState state) {
    this(state, false, FAR_FUTURE_EPOCH, PAGE_SIZE_DEFAULT, PAGE_TOKEN_DEFAULT);
  }

  @VisibleForTesting
  public BeaconValidators() {
    this.total_size = 0L;
    this.next_page_token = 0;
    this.validators = List.of();
  }

  @VisibleForTesting
  public BeaconValidators(
      tech.pegasys.teku.datastructures.state.BeaconState state,
      final boolean activeOnly,
      final UInt64 epoch,
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

  public BeaconValidators(
      tech.pegasys.teku.datastructures.state.BeaconState state, List<BLSPubKey> filter) {
    this.validators =
        filter.stream()
            .map(
                pubkey ->
                    state.getValidators().stream()
                        .filter(val -> new BLSPubKey(val.getPubkey()).equals(pubkey))
                        .map(validator -> new ValidatorWithIndex(validator, state))
                        .findFirst()
                        .orElse(new ValidatorWithIndex(pubkey)))
            .collect(Collectors.toList());
    this.total_size = null;
    this.next_page_token = null;
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
      final List<UInt64> balances,
      final boolean activeOnly,
      final UInt64 epoch,
      final int pageSize,
      final int pageToken) {

    if (pageSize > 0 && pageToken >= 0) {
      int offset = pageToken * pageSize;
      this.total_size = getEffectiveListSize(list, activeOnly, epoch);
      if (offset >= list.size()) {
        this.validators = List.of();
        this.next_page_token = 0;
        return;
      }
      validators = new ArrayList<>();
      int i = offset;
      int numberAdded = 0;
      while (i < list.size() && numberAdded < pageSize) {
        if (!activeOnly || is_active_validator(list.get(i), epoch)) {
          validators.add(new ValidatorWithIndex(list.get(i), i, balances.get(i)));
          numberAdded++;
        }
        i++;
      }
      if (total_size == 0 || offset + numberAdded >= list.size()) {
        this.next_page_token = 0;
      } else {
        this.next_page_token = pageToken + 1;
      }
    } else {
      this.validators = List.of();
      this.total_size = (long) list.size();
      this.next_page_token = 0;
    }
  }

  public static long getEffectiveListSize(List<Validator> list, boolean activeOnly, UInt64 epoch) {
    if (!activeOnly) {
      return list.size();
    } else {
      return list.stream().filter(v -> is_active_validator(v, epoch)).count();
    }
  }

  private static boolean is_active_validator(Validator validator, UInt64 epoch) {
    return validator.activation_epoch.compareTo(epoch) <= 0
        && epoch.compareTo(validator.exit_epoch) < 0;
  }
}
