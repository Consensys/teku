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

package tech.pegasys.teku.validator.remote.apiclient;

import java.util.Map;
import java.util.Map.Entry;

public enum ValidatorApiMethod {
  GET_FORK("node/fork"),
  GET_GENESIS("eth/v1/beacon/genesis"),
  GET_VALIDATORS("eth/v1/beacon/states/head/validators"),
  GET_DUTIES("validator/duties"),
  GET_UNSIGNED_BLOCK("validator/block"),
  SEND_SIGNED_BLOCK("validator/block"),
  GET_UNSIGNED_ATTESTATION("validator/attestation"),
  SEND_SIGNED_ATTESTATION("validator/attestation"),
  GET_AGGREGATE("validator/aggregate_attestation"),
  SEND_SIGNED_AGGREGATE_AND_PROOF("validator/aggregate_and_proofs"),
  SUBSCRIBE_TO_COMMITTEE_FOR_AGGREGATION("validator/beacon_committee_subscription"),
  SUBSCRIBE_TO_PERSISTENT_SUBNETS("validator/persistent_subnets_subscription"),
  GET_ATTESTATION_DUTIES("eth/v1/validator/duties/attester/:epoch"),
  GET_PROPOSER_DUTIES("eth/v1/validator/duties/proposer/:epoch"),
  EVENTS("eth/v1/events");

  private final String path;

  ValidatorApiMethod(final String path) {
    this.path = path;
  }

  public String getPath(final Map<String, String> urlParams) {
    String result = path;
    for (Entry<String, String> param : urlParams.entrySet()) {
      result = result.replace(":" + param.getKey(), param.getValue());
    }
    return result;
  }
}
