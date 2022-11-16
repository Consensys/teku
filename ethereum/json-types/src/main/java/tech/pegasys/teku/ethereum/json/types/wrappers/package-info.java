/**
 * Holds data classes specifically required to hold data for serialization as part of the REST API.
 *
 * <p>These are not intended for internal business logic and only exist when there isn't a single
 * object that holds all the data required in a REST API response. When an existing business logic
 * class does exist with all the required data, a type definition should be defined to just extract
 * the data from the existing class rather than introducing a new API specific wrapper class.
 *
 * <p>This package is definitely not a suitable place to put any classes used outside of
 * serializing/deserializing API responses.
 */
package tech.pegasys.teku.ethereum.json.types.wrappers;
