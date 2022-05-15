package tech.pegasys.teku.validator.remote.typedef;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;

import java.io.IOException;
import java.util.Optional;

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_ACCEPTED;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;

public class ResponseHandler<TObject> {
  private static final Logger LOG = LogManager.getLogger();
  private final Int2ObjectMap<ResponseHandler.Handler<TObject>> handlers = new Int2ObjectOpenHashMap<>();
  private final DeserializableTypeDefinition<TObject> typeDefinition;

  public ResponseHandler(final DeserializableTypeDefinition<TObject> typeDefinition) {
    this.typeDefinition = typeDefinition;
    withHandler(SC_OK, this::defaultOkHandler);
    withHandler(SC_ACCEPTED, this::noValueHandler);
  }

  public ResponseHandler<TObject> withHandler(final int responseCode, final Handler<TObject> handler) {
    handlers.put(responseCode, handler);
    return this;
  }

  private Optional<TObject> defaultOkHandler(final Request request, final Response response)
      throws IOException {

    return Optional.ofNullable(JsonUtil.parse(response.body().string(), typeDefinition));
  }

  public Optional<TObject> handleResponse(final Request request, final Response response)
      throws IOException {
    return handlers
        .getOrDefault(response.code(), this::unknownResponseCodeHandler)
        .handleResponse(request, response);
  }
  private Optional<TObject> unknownResponseCodeHandler(final Request request, final Response response) {
    LOG.debug(
        "Unexpected response from Beacon Node API (url = {}, status = {}, response = {})",
        request.url(),
        response.code(),
        response.body());
    throw new RuntimeException(
        String.format(
            "Unexpected response from Beacon Node API (url = %s, status = %s)",
            request.url(), response.code()));
  }
  public interface Handler<TObject> {
    Optional<TObject> handleResponse(Request request, Response response) throws IOException;
  }
  private Optional<TObject> noValueHandler(final Request request, final Response response) {
    return Optional.empty();
  }
}
