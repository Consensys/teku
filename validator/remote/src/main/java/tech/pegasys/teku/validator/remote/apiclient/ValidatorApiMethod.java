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

import static java.net.URLEncoder.encode;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.Map;

public enum ValidatorApiMethod {
  GET_GENESIS("eth/v1/beacon/genesis"),
  GET_VALIDATORS("eth/v1/beacon/states/head/validators"),
  GET_DUTIES("validator/duties"),
  GET_UNSIGNED_BLOCK("eth/v1/validator/blocks/:slot"),
  GET_UNSIGNED_BLOCK_V2("eth/v2/validator/blocks/:slot"),
  SEND_SIGNED_BLOCK("eth/v1/beacon/blocks"),
  GET_ATTESTATION_DATA("eth/v1/validator/attestation_data"),
  SEND_SIGNED_ATTESTATION("eth/v1/beacon/pool/attestations"),
  SEND_SIGNED_VOLUNTARY_EXIT("eth/v1/beacon/pool/voluntary_exits"),
  SEND_SYNC_COMMITTEE_MESSAGES("eth/v1/beacon/pool/sync_committees"),
  GET_AGGREGATE("eth/v1/validator/aggregate_attestation"),
  SEND_SIGNED_AGGREGATE_AND_PROOF("/eth/v1/validator/aggregate_and_proofs"),
  SEND_CONTRIBUTION_AND_PROOF("eth/v1/validator/contribution_and_proofs"),
  SUBSCRIBE_TO_BEACON_COMMITTEE_SUBNET("eth/v1/validator/beacon_committee_subscriptions"),
  SUBSCRIBE_TO_PERSISTENT_SUBNETS("validator/persistent_subnets_subscription"),
  SUBSCRIBE_TO_SYNC_COMMITTEE_SUBNET("eth/v1/validator/sync_committee_subscriptions"),
  GET_ATTESTATION_DUTIES("eth/v1/validator/duties/attester/:epoch"),
  GET_SYNC_COMMITTEE_DUTIES("eth/v1/validator/duties/sync/:epoch"),
  GET_SYNC_COMMITTEE_CONTRIBUTION("eth/v1/validator/sync_committee_contribution"),
  GET_PROPOSER_DUTIES("eth/v1/validator/duties/proposer/:epoch"),
  GET_BLOCK_HEADER("eth/v1/beacon/headers/:block_id"),
  GET_CONFIG_SPEC("/eth/v1/config/spec"),
  EVENTS("eth/v1/events");

  private final String path;

  ValidatorApiMethod(final String path) {
    this.path = path;
  }

  public String getPath(final Map<String, String> urlParams) {
    String result = path;
    for (final Map.Entry<String, String> param : urlParams.entrySet()) {
      result = result.replace(":" + param.getKey(), encode(param.getValue(), UTF_8));
    }
    return result;
  }
}
