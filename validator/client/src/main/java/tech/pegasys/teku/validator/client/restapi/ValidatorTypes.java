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

import static tech.pegasys.teku.infrastructure.restapi.types.CoreTypes.BOOLEAN_TYPE;
import static tech.pegasys.teku.infrastructure.restapi.types.CoreTypes.STRING_TYPE;
import static tech.pegasys.teku.infrastructure.restapi.types.DeserializableTypeDefinition.enumOf;
import static tech.pegasys.teku.infrastructure.restapi.types.SerializableTypeDefinition.listOf;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes48;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.restapi.types.CoreTypes;
import tech.pegasys.teku.infrastructure.restapi.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.types.SerializableTypeDefinition;
import tech.pegasys.teku.validator.client.Validator;
import tech.pegasys.teku.validator.client.restapi.apis.schema.DeleteKeyResult;
import tech.pegasys.teku.validator.client.restapi.apis.schema.DeleteKeysRequest;
import tech.pegasys.teku.validator.client.restapi.apis.schema.DeleteKeysResponse;
import tech.pegasys.teku.validator.client.restapi.apis.schema.DeletionStatus;
import tech.pegasys.teku.validator.client.restapi.apis.schema.ImportStatus;
import tech.pegasys.teku.validator.client.restapi.apis.schema.PostKeyResult;
import tech.pegasys.teku.validator.client.restapi.apis.schema.PostKeysRequest;

public class ValidatorTypes {

  static SerializableTypeDefinition<PostKeyResult> POST_KEY_RESULT =
      SerializableTypeDefinition.<PostKeyResult>object()
          .name("PostKeyResult")
          .withField("status", enumOf(ImportStatus.class), PostKeyResult::getImportStatus)
          .withOptionalField("message", STRING_TYPE, PostKeyResult::getMessage)
          .build();

  public static final SerializableTypeDefinition<List<PostKeyResult>> POST_KEYS_RESPONSE =
      SerializableTypeDefinition.<List<PostKeyResult>>object()
          .name("PostKeysResponse")
          .withField("data", listOf(POST_KEY_RESULT), Function.identity())
          .build();

  public static final DeserializableTypeDefinition<PostKeysRequest> POST_KEYS_REQUEST =
      DeserializableTypeDefinition.object(PostKeysRequest.class)
          .name("PostKeysRequest")
          .initializer(PostKeysRequest::new)
          .withField(
              "keystores",
              DeserializableTypeDefinition.listOf(STRING_TYPE),
              PostKeysRequest::getKeystores,
              PostKeysRequest::setKeystores)
          .withField(
              "passwords",
              DeserializableTypeDefinition.listOf(STRING_TYPE),
              PostKeysRequest::getPasswords,
              PostKeysRequest::setPasswords)
          .withOptionalField(
              "slashing_protection",
              CoreTypes.string(
                  "JSON serialized representation of the slash protection data in format defined in EIP-3076: Slashing Protection Interchange Format."),
              PostKeysRequest::getSlashingProtection,
              PostKeysRequest::setSlashingProtection)
          .build();
  public static DeserializableTypeDefinition<BLSPublicKey> PUBKEY_TYPE =
      DeserializableTypeDefinition.string(BLSPublicKey.class)
          .name("PubKey")
          .formatter(BLSPublicKey::toString)
          .parser(value -> BLSPublicKey.fromBytesCompressedValidate(Bytes48.fromHexString(value)))
          .pattern("^0x[a-fA-F0-9]{96}$")
          .example(
              "0x93247f2209abcacf57b75a51dafae777f9dd38bc7053d1af526f220a7489a6d3a2753e5f3e8b1cfe39b56f43611df74a")
          .description(
              "The validator's BLS public key, uniquely identifying them. _48-bytes, hex encoded with 0x prefix, case insensitive._")
          .build();

  public static SerializableTypeDefinition<Validator> ACTIVE_VALIDATOR =
      SerializableTypeDefinition.object(Validator.class)
          .withField("validating_pubkey", PUBKEY_TYPE, Validator::getPublicKey)
          .withOptionalField(
              "derivation_path",
              CoreTypes.string("The derivation path (if present in the imported keystore)."),
              __ -> Optional.empty())
          .withField("readonly", BOOLEAN_TYPE, Validator::isReadOnly)
          .build();

  public static SerializableTypeDefinition<List<Validator>> LIST_KEYS_RESPONSE_TYPE =
      SerializableTypeDefinition.<List<Validator>>object()
          .name("ListKeysResponse")
          .withField("data", listOf(ACTIVE_VALIDATOR), Function.identity())
          .build();

  static SerializableTypeDefinition<DeleteKeyResult> DELETE_KEY_RESULT =
      SerializableTypeDefinition.object(DeleteKeyResult.class)
          .name("DeleteKeyResult")
          .withField("status", enumOf(DeletionStatus.class), DeleteKeyResult::getStatus)
          .withOptionalField("message", STRING_TYPE, DeleteKeyResult::getMessage)
          .build();

  public static SerializableTypeDefinition<DeleteKeysResponse> DELETE_KEYS_RESPONSE_TYPE =
      SerializableTypeDefinition.object(DeleteKeysResponse.class)
          .name("DeleteKeysResponse")
          .withField("data", listOf(DELETE_KEY_RESULT), DeleteKeysResponse::getData)
          .withField("slashing_protection", STRING_TYPE, DeleteKeysResponse::getSlashingProtection)
          .build();

  public static DeserializableTypeDefinition<DeleteKeysRequest> DELETE_KEYS_REQUEST =
      DeserializableTypeDefinition.object(DeleteKeysRequest.class)
          .name("DeleteKeysRequest")
          .initializer(DeleteKeysRequest::new)
          .withField(
              "pubkeys",
              DeserializableTypeDefinition.listOf(PUBKEY_TYPE),
              DeleteKeysRequest::getPublicKeys,
              DeleteKeysRequest::setPublicKeys)
          .build();
}
