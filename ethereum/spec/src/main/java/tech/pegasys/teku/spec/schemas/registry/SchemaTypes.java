/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.spec.schemas.registry;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CaseFormat;
import com.google.common.base.Converter;
import com.google.common.base.MoreObjects;
import java.util.Locale;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationSchema;

public class SchemaTypes {
  // PHASE0
  public static final SchemaId<SszBitvectorSchema<SszBitvector>> ATTNETS_ENR_FIELD_SCHEMA =
      create("ATTNETS_ENR_FIELD_SCHEMA");

  public static final SchemaId<AttestationSchema<Attestation>> ATTESTATION_SCHEMA =
      create("ATTESTATION_SCHEMA");

  // Altair

  // Bellatrix

  // Capella

  // Deneb

  private SchemaTypes() {
    // Prevent instantiation
  }

  @VisibleForTesting
  static <T> SchemaId<T> create(final String name) {
    return new SchemaId<>(name);
  }

  public static class SchemaId<T> {
    private static final Converter<String, String> UPPER_UNDERSCORE_TO_UPPER_CAMEL =
        CaseFormat.UPPER_UNDERSCORE.converterTo(CaseFormat.UPPER_CAMEL);

    public static String upperSnakeCaseToUpperCamel(final String camelCase) {
      return UPPER_UNDERSCORE_TO_UPPER_CAMEL.convert(camelCase);
    }

    private static String capitalizeMilestone(final SpecMilestone milestone) {
      return milestone.name().charAt(0) + milestone.name().substring(1).toLowerCase(Locale.ROOT);
    }

    private final String name;

    private SchemaId(final String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }

    public String getContainerName(final SpecMilestone milestone) {
      return getContainerName() + capitalizeMilestone(milestone);
    }

    public String getContainerName() {
      return upperSnakeCaseToUpperCamel(name.replace("_SCHEMA", ""));
    }

    @Override
    public int hashCode() {
      return name.hashCode();
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o instanceof SchemaId<?> other) {
        return name.equals(other.name);
      }
      return false;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this).add("name", name).toString();
    }
  }
}
