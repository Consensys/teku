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

import com.google.common.primitives.UnsignedLong;
import java.util.List;

public class ValidatorDuties {
  public final BLSPubKey validator_pubkey;
  public final Integer aggregator_modulo;
  public final Integer validator_index;
  public final Integer attestation_committee_index;
  public final Integer attestation_committee_position;
  public final List<UnsignedLong> block_proposal_slots;
  public final UnsignedLong attestation_slot;

  public ValidatorDuties(
      BLSPubKey validator_pubkey,
      Integer validator_index,
      Integer attestation_committee_index,
      Integer attestation_committee_position,
      Integer aggregator_modulo,
      List<UnsignedLong> block_proposal_slots,
      UnsignedLong attestation_slot) {
    this.validator_pubkey = validator_pubkey;
    this.validator_index = validator_index;
    this.attestation_committee_index = attestation_committee_index;
    this.attestation_committee_position = attestation_committee_position;
    this.aggregator_modulo = aggregator_modulo;
    this.block_proposal_slots = block_proposal_slots;
    this.attestation_slot = attestation_slot;
  }
}
