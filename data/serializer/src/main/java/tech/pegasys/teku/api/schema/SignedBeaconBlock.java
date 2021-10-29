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

import static tech.pegasys.teku.api.schema.SchemaConstants.DESCRIPTION_BYTES96;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import tech.pegasys.teku.api.schema.altair.BeaconBlockAltair;
import tech.pegasys.teku.api.schema.interfaces.SignedBlock;
import tech.pegasys.teku.api.schema.merge.BeaconBlockMerge;
import tech.pegasys.teku.api.schema.phase0.BeaconBlockPhase0;
import tech.pegasys.teku.spec.Spec;

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = SignedBeaconBlock.SignedBeaconBlockMerge.class),
  @JsonSubTypes.Type(value = SignedBeaconBlock.SignedBeaconBlockAltair.class),
  @JsonSubTypes.Type(value = SignedBeaconBlock.SignedBeaconBlockPhase0.class),
})
public abstract class SignedBeaconBlock<T extends BeaconBlock> implements SignedBlock {
  private final T message;

  @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES96)
  public final BLSSignature signature;

  public T getMessage() {
    return message;
  }

  private SignedBeaconBlock(
      tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock internalBlock, T block) {
    this.signature = new BLSSignature(internalBlock.getSignature());
    this.message = block;
  }

  private SignedBeaconBlock(final T message, final BLSSignature signature) {
    this.message = message;
    this.signature = signature;
  }

  public tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock asInternalSignedBeaconBlock(
      final Spec spec) {
    final tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock beaconBlock =
        getMessage().asInternalBeaconBlock(spec);
    return tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock.create(
        spec, beaconBlock, signature.asInternalBLSSignature());
  }

  public static SignedBeaconBlock<?> create(
      tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock internalBlock) {
    tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody beaconBlock =
        internalBlock.getMessage().getBody();

    return Stream.of(
            () -> beaconBlock.toVersionMerge().map(__ -> new SignedBeaconBlockMerge(internalBlock)),
            () ->
                beaconBlock.toVersionAltair().map(__ -> new SignedBeaconBlockAltair(internalBlock)),
            (Supplier<Optional<SignedBeaconBlock<?>>>)
                () ->
                    Optional.of((SignedBeaconBlock<?>) new SignedBeaconBlockPhase0(internalBlock)))
        .map(Supplier::get)
        .flatMap(Optional::stream)
        .findFirst()
        .orElseThrow();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SignedBeaconBlock<?>)) return false;
    SignedBeaconBlock<?> that = (SignedBeaconBlock<?>) o;
    return Objects.equals(message, that.message) && Objects.equals(signature, that.signature);
  }

  @Override
  public int hashCode() {
    return Objects.hash(message, signature);
  }

  public static class SignedBeaconBlockMerge extends SignedBeaconBlock<BeaconBlockMerge> {
    @JsonCreator
    private SignedBeaconBlockMerge(
        @JsonProperty("message") final BeaconBlockMerge message,
        @JsonProperty("signature") final BLSSignature signature) {
      super(message, signature);
    }

    private SignedBeaconBlockMerge(
        tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock internalBlock) {
      super(internalBlock, new BeaconBlockMerge(internalBlock.getMessage()));
    }
  }

  public static class SignedBeaconBlockAltair extends SignedBeaconBlock<BeaconBlockAltair> {
    @JsonCreator
    private SignedBeaconBlockAltair(
        @JsonProperty("message") final BeaconBlockAltair message,
        @JsonProperty("signature") final BLSSignature signature) {
      super(message, signature);
    }

    private SignedBeaconBlockAltair(
        tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock internalBlock) {
      super(internalBlock, new BeaconBlockAltair(internalBlock.getMessage()));
    }
  }

  public static class SignedBeaconBlockPhase0 extends SignedBeaconBlock<BeaconBlockPhase0> {
    @JsonCreator
    private SignedBeaconBlockPhase0(
        @JsonProperty("message") final BeaconBlockPhase0 message,
        @JsonProperty("signature") final BLSSignature signature) {
      super(message, signature);
    }

    private SignedBeaconBlockPhase0(
        tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock internalBlock) {
      super(internalBlock, new BeaconBlockPhase0(internalBlock.getMessage()));
    }
  }
}
