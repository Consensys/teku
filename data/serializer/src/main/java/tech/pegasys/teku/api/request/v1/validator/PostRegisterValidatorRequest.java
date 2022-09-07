/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.api.request.v1.validator;

import static tech.pegasys.teku.api.schema.SchemaConstants.DESCRIPTION_BYTES96;
import static tech.pegasys.teku.api.schema.SchemaConstants.DESCRIPTION_EXECUTION_ADDRESS;
import static tech.pegasys.teku.api.schema.SchemaConstants.EXAMPLE_EXECUTION_ADDRESS;
import static tech.pegasys.teku.api.schema.SchemaConstants.EXAMPLE_PUBKEY;
import static tech.pegasys.teku.api.schema.SchemaConstants.EXAMPLE_UINT64;
import static tech.pegasys.teku.api.schema.SchemaConstants.PATTERN_EXECUTION_ADDRESS;
import static tech.pegasys.teku.api.schema.SchemaConstants.PATTERN_PUBKEY;

import io.swagger.v3.oas.annotations.media.Schema;
import tech.pegasys.teku.api.schema.BLSPubKey;
import tech.pegasys.teku.api.schema.BLSSignature;
import tech.pegasys.teku.infrastructure.bytes.Eth1Address;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

@SuppressWarnings("JavaCase")
public class PostRegisterValidatorRequest {
  public ValidatorRegistration message;

  @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES96)
  public BLSSignature signature;

  public static class ValidatorRegistration {

    @Schema(
        type = "string",
        pattern = PATTERN_EXECUTION_ADDRESS,
        example = EXAMPLE_EXECUTION_ADDRESS,
        description = DESCRIPTION_EXECUTION_ADDRESS)
    public Eth1Address fee_recipient;

    @Schema(type = "string", format = "uint64", example = EXAMPLE_UINT64)
    public UInt64 gas_limit;

    @Schema(type = "string", format = "uint64", example = EXAMPLE_UINT64)
    public UInt64 timestamp;

    @Schema(
        type = "string",
        pattern = PATTERN_PUBKEY,
        example = EXAMPLE_PUBKEY,
        description =
            "The validator's BLS public key, uniquely identifying them. "
                + "48-bytes, hex encoded with 0x prefix, case insensitive.")
    public BLSPubKey pubkey;
  }
}
