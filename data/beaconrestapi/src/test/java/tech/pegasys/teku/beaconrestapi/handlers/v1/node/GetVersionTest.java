package tech.pegasys.teku.beaconrestapi.handlers.v1.node;

import io.javalin.core.util.Header;
import io.javalin.http.Context;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.response.v1.node.Version;
import tech.pegasys.teku.api.response.v1.node.VersionResponse;
import tech.pegasys.teku.provider.JsonProvider;
import tech.pegasys.teku.util.cli.VersionProvider;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static tech.pegasys.teku.beaconrestapi.CacheControlUtils.CACHE_NONE;

public class GetVersionTest {
  private Context context = mock(Context.class);
  private final JsonProvider jsonProvider = new JsonProvider();
  private final VersionResponse versionResponse = new VersionResponse(new Version(VersionProvider.VERSION));

  @Test
  public void shouldReturnVersionString() throws Exception {
    GetVersion handler = new GetVersion(jsonProvider);
    handler.handle(context);
    verify(context).header(Header.CACHE_CONTROL, CACHE_NONE);
    verify(context).result(jsonProvider.objectToJSON(versionResponse));
  }
}
