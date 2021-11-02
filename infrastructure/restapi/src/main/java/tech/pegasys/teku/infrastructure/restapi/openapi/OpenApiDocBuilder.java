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

package tech.pegasys.teku.infrastructure.restapi.openapi;

import static com.google.common.base.Preconditions.checkNotNull;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;

public class OpenApiDocBuilder {

  public static final String OPENAPI_VERSION = "3.0.1";

  // Info
  private String title;
  private String version;
  private String description;
  private String licenseName;
  private String licenseUrl;

  public OpenApiDocBuilder title(final String title) {
    this.title = title;
    return this;
  }

  public OpenApiDocBuilder version(final String version) {
    this.version = version;
    return this;
  }

  public OpenApiDocBuilder description(final String description) {
    this.description = description;
    return this;
  }

  public OpenApiDocBuilder license(final String licenseName, final String licenseUrl) {
    this.licenseName = licenseName;
    this.licenseUrl = licenseUrl;
    return this;
  }

  public String build() {
    checkNotNull(title, "title must be supplied");
    checkNotNull(version, "version must be supplied");
    final StringWriter writer = new StringWriter();
    try (final JsonGenerator gen = new ObjectMapper().createGenerator(writer)) {

      gen.writeStartObject();
      gen.writeStringField("openapi", OPENAPI_VERSION);
      gen.writeFieldName("info");
      writeInfo(gen);
      gen.writeEndObject();

    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return writer.toString();
  }

  private void writeInfo(final JsonGenerator gen) throws IOException {
    gen.writeStartObject();
    gen.writeStringField("title", title);
    if (description != null) {
      gen.writeStringField("description", description);
    }
    if (licenseName != null) {
      gen.writeObjectFieldStart("license");
      gen.writeStringField("name", licenseName);
      gen.writeStringField("url", licenseUrl);
      gen.writeEndObject();
    }
    gen.writeStringField("version", version);
    gen.writeEndObject();
  }
}
