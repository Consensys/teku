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

package tech.pegasys.teku.cli.subcommand.debug;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import java.io.IOException;
import java.util.Map;
import tech.pegasys.teku.data.yaml.YamlProvider;
import tech.pegasys.teku.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.protoarray.BlockInformation;
import tech.pegasys.teku.protoarray.ProtoArraySnapshot;

public class ForkChoiceDataWriter {

  public static String writeForkChoiceData(
      final ProtoArraySnapshot snapshot, final Map<UInt64, VoteTracker> votes) throws IOException {
    final SimpleModule forkChoiceModule =
        new SimpleModule()
            .addSerializer(ProtoArraySnapshot.class, new ProtoArraySnapshotSerializer())
            .addSerializer(VoteTracker.class, new VoteSerializer());
    final YamlProvider mapper = new YamlProvider(forkChoiceModule);
    return mapper.writeString(Map.of("snapshot", snapshot, "votes", votes));
  }

  private static class VoteSerializer extends JsonSerializer<VoteTracker> {

    @Override
    public void serialize(
        final VoteTracker value, final JsonGenerator gen, final SerializerProvider serializers)
        throws IOException {
      gen.writeStartObject();
      gen.writeStringField("currentRoot", value.getCurrentRoot().toHexString());
      gen.writeStringField("nextRoot", value.getNextRoot().toHexString());
      gen.writeNumberField("nextEpoch", value.getNextEpoch().bigIntegerValue());
      gen.writeEndObject();
    }
  }

  private static class ProtoArraySnapshotSerializer extends JsonSerializer<ProtoArraySnapshot> {
    @Override
    public void serialize(
        final ProtoArraySnapshot value,
        final JsonGenerator gen,
        final SerializerProvider serializers)
        throws IOException {
      gen.writeStartObject();
      gen.writeNumberField("justifiedEpoch", value.getJustifiedEpoch().bigIntegerValue());
      gen.writeNumberField("finalizedEpoch", value.getFinalizedEpoch().bigIntegerValue());
      gen.writeArrayFieldStart("blockInformation");
      for (BlockInformation blockInformation : value.getBlockInformationList()) {
        gen.writeStartObject();
        gen.writeNumberField("blockSlot", blockInformation.getBlockSlot().bigIntegerValue());
        gen.writeStringField("blockRoot", blockInformation.getBlockRoot().toHexString());
        gen.writeStringField("parentRoot", blockInformation.getParentRoot().toHexString());
        gen.writeStringField("stateRoot", blockInformation.getStateRoot().toHexString());
        gen.writeNumberField(
            "justifiedEpoch", blockInformation.getJustifiedEpoch().bigIntegerValue());
        gen.writeNumberField(
            "finalizedEpoch", blockInformation.getFinalizedEpoch().bigIntegerValue());
        gen.writeEndObject();
      }
      gen.writeEndArray();
      gen.writeEndObject();
    }
  }
}
