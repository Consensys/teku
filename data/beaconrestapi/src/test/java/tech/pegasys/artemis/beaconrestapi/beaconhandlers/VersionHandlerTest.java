package tech.pegasys.artemis.beaconrestapi.beaconhandlers;

import io.javalin.http.Context;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tech.pegasys.artemis.provider.JsonProvider;
import tech.pegasys.artemis.util.cli.VersionProvider;

public class VersionHandlerTest {
  private Context mockContext = Mockito.mock(Context.class);
  @Test
  public void shouldReturnVersionString() throws Exception {
    VersionHandler subject = new VersionHandler();
    subject.handle(mockContext);

    Mockito.verify(mockContext).result(JsonProvider.objectToJSON(VersionProvider.VERSION));
  }
}
