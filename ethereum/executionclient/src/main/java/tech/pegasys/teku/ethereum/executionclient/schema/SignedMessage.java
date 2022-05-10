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

package tech.pegasys.teku.ethereum.executionclient.schema;

import static com.google.common.base.Preconditions.checkNotNull;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.MoreObjects;
import java.util.Objects;
import tech.pegasys.teku.ethereum.executionclient.serialization.BLSSignatureDeserializer;
import tech.pegasys.teku.ethereum.executionclient.serialization.BLSSignatureSerializer;

public class SignedMessage<T> {
  private final T message;

  @JsonSerialize(using = BLSSignatureSerializer.class)
  @JsonDeserialize(using = BLSSignatureDeserializer.class)
  private final BLSSignature signature;

  @JsonCreator
  public SignedMessage(
      @JsonProperty("message") T message, @JsonProperty("signature") BLSSignature signature) {
    checkNotNull(message, "message cannot be null");
    checkNotNull(signature, "signature cannot be null");
    this.message = message;
    this.signature = signature;
  }

  public SignedMessage(T message, tech.pegasys.teku.bls.BLSSignature signature) {
    checkNotNull(message, "message cannot be null");
    checkNotNull(signature, "signature cannot be null");
    this.message = message;
    this.signature = new BLSSignature(signature);
  }

  public T getMessage() {
    return message;
  }

  public BLSSignature getSignature() {
    return signature;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final SignedMessage<?> that = (SignedMessage<?>) o;
    return Objects.equals(message, that.message) && Objects.equals(signature, that.signature);
  }

  @Override
  public int hashCode() {
    return Objects.hash(message, signature);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("message", message)
        .add("signature", signature)
        .toString();
  }
}
