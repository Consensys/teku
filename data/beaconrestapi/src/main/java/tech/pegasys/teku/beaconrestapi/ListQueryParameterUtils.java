package tech.pegasys.teku.beaconrestapi;

import org.apache.commons.lang3.StringUtils;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ListQueryParameterUtils {
  public static final String MISSING_QUERY_PARAMETER = "Query parameter %s not found";
  public static List<String> validateQueryParameter(
      final Map<String, List<String>> parameterMap, final String key)
      throws IllegalArgumentException {
    if (parameterMap.containsKey(key)) {
      final List<String> values = parameterMap.get(key);
      if (values.isEmpty()) {
        throw new IllegalArgumentException(String.format(MISSING_QUERY_PARAMETER, key));
      }
      return parameterMap.get(key);
    }
    throw new IllegalArgumentException(String.format(MISSING_QUERY_PARAMETER, key));
  }

  public static List<Integer> getParameterAsIntegerList(final Map<String, List<String>> parameterMap, final String key) throws IllegalArgumentException {
    try {
      List<String> list = validateQueryParameter(parameterMap, key);
      return list.stream()
          .map(Integer::parseUnsignedInt).collect(Collectors.toList());
    } catch (NumberFormatException ex){
      throw new IllegalArgumentException(String.format("Failed to parse query parameter %s to Integer", key), ex);
    }
  }
}
