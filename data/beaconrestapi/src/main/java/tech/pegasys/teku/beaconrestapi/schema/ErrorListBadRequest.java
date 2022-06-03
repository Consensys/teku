/*
 * Copyright 2022 ConsenSys AG.
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

package tech.pegasys.teku.beaconrestapi.schema;

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.INTEGER_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.STRING_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition.listOf;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.validator.api.SubmitDataError;

public class ErrorListBadRequest {
  private static final int CODE = SC_BAD_REQUEST;
  private final String message;
  private final List<SubmitDataError> errors;

  public ErrorListBadRequest(final String message, final List<SubmitDataError> errors) {
    this.message = message;
    this.errors = errors;
  }

  public int getCode() {
    return CODE;
  }

  public String getMessage() {
    return message;
  }

  public List<SubmitDataError> getErrors() {
    return errors;
  }

  public static ErrorListBadRequest convert(
      final String message, final List<SubmitDataError> errors) {
    return new ErrorListBadRequest(message, errors);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ErrorListBadRequest that = (ErrorListBadRequest) o;
    return Objects.equals(message, that.message) && Objects.equals(errors, that.errors);
  }

  @Override
  public int hashCode() {
    return Objects.hash(message, errors);
  }

  @Override
  public String toString() {
    return "ErrorListBadRequest{" + "message='" + message + '\'' + ", errors=" + errors + '}';
  }

  public static SerializableTypeDefinition<ErrorListBadRequest> getStacktraceTypeDefinition() {
    return SerializableTypeDefinition.object(ErrorListBadRequest.class)
        .name("StacktraceErrorListBadRequest")
        .withField("code", INTEGER_TYPE, ErrorListBadRequest::getCode)
        .withField("message", STRING_TYPE, ErrorListBadRequest::getMessage)
        .withField(
            "stacktraces",
            SerializableTypeDefinition.listOf(STRING_TYPE),
            data ->
                data.getErrors().stream()
                    .map(SubmitDataError::getMessage)
                    .collect(Collectors.toList()))
        .build();
  }

  public static SerializableTypeDefinition<ErrorListBadRequest> getFailuresTypeDefinition() {
    return SerializableTypeDefinition.object(ErrorListBadRequest.class)
        .name("FailuresErrorListBadRequest")
        .withField("code", INTEGER_TYPE, ErrorListBadRequest::getCode)
        .withField("message", STRING_TYPE, ErrorListBadRequest::getMessage)
        .withField(
            "failures",
            listOf(SubmitDataError.getJsonTypeDefinition()),
            ErrorListBadRequest::getErrors)
        .build();
  }
}
