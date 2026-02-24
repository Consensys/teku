/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.beaconrestapi.handlers.v2.node;

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.CACHE_NONE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.TAG_NODE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BYTES4_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.STRING_TYPE;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.http.Header;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import tech.pegasys.teku.api.DataProvider;
import tech.pegasys.teku.api.ExecutionClientDataProvider;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.restapi.endpoints.EndpointMetadata;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiEndpoint;
import tech.pegasys.teku.infrastructure.restapi.endpoints.RestApiRequest;
import tech.pegasys.teku.infrastructure.version.VersionProvider;
import tech.pegasys.teku.spec.datastructures.execution.ClientVersion;

public class GetVersionV2 extends RestApiEndpoint {
  public static final String ROUTE = "/eth/v2/node/version";

  private static final SerializableTypeDefinition<ExecutionClientInfo> EXECUTION_CLIENT_TYPE =
      SerializableTypeDefinition.object(ExecutionClientInfo.class)
          .withOptionalField("code", STRING_TYPE, ExecutionClientInfo::getCode)
          .withOptionalField("name", STRING_TYPE, ExecutionClientInfo::getName)
          .withOptionalField("version", STRING_TYPE, ExecutionClientInfo::getVersion)
          .withOptionalField("commit", BYTES4_TYPE, ExecutionClientInfo::getCommit)
          .build();

  private static final SerializableTypeDefinition<VersionDataV2> DATA_TYPE =
      SerializableTypeDefinition.object(VersionDataV2.class)
          .withField("version", STRING_TYPE, VersionDataV2::getVersion)
          .withOptionalField("execution_client", EXECUTION_CLIENT_TYPE, VersionDataV2::getExecutionClient)
          .build();

  private static final SerializableTypeDefinition<VersionDataV2> RESPONSE_TYPE =
      SerializableTypeDefinition.object(VersionDataV2.class)
          .name("GetVersionV2Response")
          .withField("data", DATA_TYPE, Function.identity())
          .build();

  private final ExecutionClientDataProvider executionClientDataProvider;

  public GetVersionV2(final DataProvider dataProvider) {
    this(dataProvider.getExecutionClientDataProvider());
  }

  public GetVersionV2(final ExecutionClientDataProvider executionClientDataProvider) {
    super(
        EndpointMetadata.get(ROUTE)
            .operationId("getNodeVersionV2")
            .summary("Get version string of the running beacon node.")
            .description(
                "Returns the version information of the beacon node and the execution client. "
                    + "Similar to [HTTP User-Agent](https://tools.ietf.org/html/rfc7231#section-5.5.3).")
            .tags(TAG_NODE)
            .response(SC_OK, "Request successful", RESPONSE_TYPE)
            .build());
    this.executionClientDataProvider = executionClientDataProvider;
  }

  @Override
  public void handleRequest(final RestApiRequest request) throws JsonProcessingException {
    request.header(Header.CACHE_CONTROL, CACHE_NONE);
    final Optional<ClientVersion> executionClientVersion =
        executionClientDataProvider.getExecutionClientVersion();
    final Optional<ExecutionClientInfo> executionClient =
        executionClientVersion.map(ExecutionClientInfo::fromClientVersion);
    request.respondOk(new VersionDataV2(VersionProvider.VERSION, executionClient));
  }

  static class ExecutionClientInfo {
    private final String code;
    private final String name;
    private final String version;
    private final Bytes4 commit;

    public ExecutionClientInfo(
        final String code, final String name, final String version, final Bytes4 commit) {
      this.code = code;
      this.name = name;
      this.version = version;
      this.commit = commit;
    }

    public static ExecutionClientInfo fromClientVersion(final ClientVersion clientVersion) {
      return new ExecutionClientInfo(
          clientVersion.code(),
          clientVersion.name(),
          clientVersion.version(),
          clientVersion.commit());
    }

    public String getCode() {
      return code;
    }

    public String getName() {
      return name;
    }

    public String getVersion() {
      return version;
    }

    public Bytes4 getCommit() {
      return commit;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final ExecutionClientInfo that = (ExecutionClientInfo) o;
      return Objects.equals(code, that.code)
          && Objects.equals(name, that.name)
          && Objects.equals(version, that.version)
          && Objects.equals(commit, that.commit);
    }

    @Override
    public int hashCode() {
      return Objects.hash(code, name, version, commit);
    }
  }

  static class VersionDataV2 {
    private final String version;
    private final Optional<ExecutionClientInfo> executionClient;

    public VersionDataV2(final String version, final Optional<ExecutionClientInfo> executionClient) {
      this.version = version;
      this.executionClient = executionClient;
    }

    public String getVersion() {
      return version;
    }

    public Optional<ExecutionClientInfo> getExecutionClient() {
      return executionClient;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final VersionDataV2 that = (VersionDataV2) o;
      return Objects.equals(version, that.version)
          && Objects.equals(executionClient, that.executionClient);
    }

    @Override
    public int hashCode() {
      return Objects.hash(version, executionClient);
    }
  }
}
