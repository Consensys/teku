/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.validator.client.restapi;

import java.util.List;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes48;
import tech.pegasys.teku.api.schema.PublicKeyException;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.restapi.types.CoreTypes;
import tech.pegasys.teku.infrastructure.restapi.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.types.SerializableTypeDefinition;
import tech.pegasys.teku.validator.client.restapi.apis.schema.DeleteKeyResult;
import tech.pegasys.teku.validator.client.restapi.apis.schema.DeletionStatus;

public class ValidatorTypes {
  public static DeserializableTypeDefinition<BLSPublicKey> PUBKEY_TYPE =
      DeserializableTypeDefinition.string(BLSPublicKey.class)
          .name("PubKey")
          .formatter(BLSPublicKey::toString)
          .parser(
              value -> {
                try {
                  return BLSPublicKey.fromBytesCompressedValidate(Bytes48.fromHexString(value));
                } catch (final IllegalArgumentException e) {
                  throw new PublicKeyException(
                      "Public key " + value + " is invalid: " + e.getMessage(), e);
                }
              })
          .pattern("^0x[a-fA-F0-9]{96}$")
          .example(
              "0x93247f2209abcacf57b75a51dafae777f9dd38bc7053d1af526f220a7489a6d3a2753e5f3e8b1cfe39b56f43611df74a")
          .description(
              "The validator's BLS public key, uniquely identifying them. _48-bytes, hex encoded with 0x prefix, case insensitive._")
          .build();

  public static SerializableTypeDefinition<BLSPublicKey> VALIDATOR_KEY_TYPE =
      SerializableTypeDefinition.object(BLSPublicKey.class)
          .withField("validating_pubkey", PUBKEY_TYPE, Function.identity())
          .withField(
              "derivation_path",
              CoreTypes.string("The derivation path (if present in the imported keystore)."),
              __ -> null)
          .build();

  public static SerializableTypeDefinition<List<BLSPublicKey>> LIST_KEYS_RESPONSE_TYPE =
      SerializableTypeDefinition.<List<BLSPublicKey>>object()
          .name("ListKeysResponse")
          .withField(
              "keys", SerializableTypeDefinition.listOf(VALIDATOR_KEY_TYPE), Function.identity())
          .build();

  static SerializableTypeDefinition<DeleteKeyResult> DELETE_KEY_RESULT =
      SerializableTypeDefinition.object(DeleteKeyResult.class)
          .name("DeleteKeyResult")
          .withField(
              "status", DeserializableTypeDefinition.enumOf(DeletionStatus.class), __ -> null)
          .withOptionalField("message", CoreTypes.STRING_TYPE, DeleteKeyResult::getMessage)
          .withOptionalField(
              "slashing_protection", CoreTypes.STRING_TYPE, DeleteKeyResult::getSlashingProtection)
          .build();

  public static SerializableTypeDefinition<List<DeleteKeyResult>> DELETE_KEYS_RESPONSE_TYPE =
      SerializableTypeDefinition.<List<DeleteKeyResult>>object()
          .name("DeleteKeysResponse")
          .withField(
              "data", SerializableTypeDefinition.listOf(DELETE_KEY_RESULT), Function.identity())
          .build();
}
