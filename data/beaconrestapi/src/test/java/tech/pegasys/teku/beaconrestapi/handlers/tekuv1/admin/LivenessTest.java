package tech.pegasys.teku.beaconrestapi.handlers.tekuv1.admin;

import io.javalin.http.Context;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class LivenessTest {

  private Liveness handler;

  @BeforeEach
  public void setup() {
    handler = new Liveness();
  }

  private Context context = mock(Context.class);

  @Test
  public void shouldReturnOkWhenTekuIsUp() throws Exception {
    handler.handle(context);
    verify(context).status(SC_OK);
  }
}
