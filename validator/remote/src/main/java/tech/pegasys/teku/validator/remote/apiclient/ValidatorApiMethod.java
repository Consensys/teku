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

public enum ValidatorApiMethod {
  GET_FORK("/node/fork"),
  GET_DUTIES("/validator/duties"),
  GET_UNSIGNED_BLOCK("/validator/block"),
  SEND_SIGNED_BLOCK("/validator/block"),
  GET_UNSIGNED_ATTESTATION("/validator/attestation"),
  SEND_SIGNED_ATTESTATION("/validator/attestation"),
  GET_AGGREGATE("/validator/aggregate_attestation"),
  SEND_SIGNED_AGGREGATE_AND_PROOF("/validator/aggregate_and_proofs"),
  SUBSCRIBE_TO_COMMITTEE_FOR_AGGREGATION("/validator/beacon_committee_subscription"),
  SUBSCRIBE_TO_PERSISTENT_SUBNETS("/validator/persistent_subnets_subscription");

  private final String path;

  ValidatorApiMethod(final String path) {
    this.path = path;
  }

  public String getPath() {
    return path;
  }
}
