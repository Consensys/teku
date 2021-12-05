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

package tech.pegasys.teku.spec.schemas;

import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.spec.constants.NetworkConstants;
import tech.pegasys.teku.util.config.Constants;

public abstract class AbstractSchemaDefinitions implements SchemaDefinitions {

  final SszBitvectorSchema<SszBitvector> attnetsENRFieldSchema =
      SszBitvectorSchema.create(Constants.ATTESTATION_SUBNET_COUNT);
  final SszBitvectorSchema<SszBitvector> syncnetsENRFieldSchema =
      SszBitvectorSchema.create(NetworkConstants.SYNC_COMMITTEE_SUBNET_COUNT);

  @Override
  public SszBitvectorSchema<SszBitvector> getAttnetsENRFieldSchema() {
    return attnetsENRFieldSchema;
  }

  @Override
  public SszBitvectorSchema<SszBitvector> getSyncnetsENRFieldSchema() {
    return syncnetsENRFieldSchema;
  }
}
