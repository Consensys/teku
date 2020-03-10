package tech.pegasys.artemis.beaconrestapi.validatorhandlers;

import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.primitives.UnsignedLong;
import io.javalin.http.Context;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.pegasys.artemis.api.ChainDataProvider;
import tech.pegasys.artemis.beaconrestapi.beaconhandlers.BeaconValidatorsHandler;
import tech.pegasys.artemis.datastructures.util.DataStructureUtil;
import tech.pegasys.artemis.provider.JsonProvider;
import tech.pegasys.artemis.util.async.SafeFuture;

@ExtendWith(MockitoExtension.class)
public class ValidatorDutiesHandlerTest {
  private Context context = mock(Context.class);
//  private final UnsignedLong epoch = DataStructureUtil.randomUnsignedLong(99);
  private final JsonProvider jsonProvider = new JsonProvider();
  private final ChainDataProvider provider = mock(ChainDataProvider.class);
//
//  @Captor
//  private ArgumentCaptor<SafeFuture<String>> args;

  @Test
  public void shouldReturnNoContentWhenNoBlockRoot() throws Exception {
    BeaconValidatorsHandler handler = new BeaconValidatorsHandler(provider, jsonProvider);
    when(provider.getBestBlockRoot()).thenReturn(Optional.empty());
    handler.handle(context);
    verify(context).status(SC_NO_CONTENT);
  }
}
