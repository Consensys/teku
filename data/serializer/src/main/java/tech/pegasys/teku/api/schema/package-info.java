/**
 * Package containing schema classes for Teku's API.
 * 
 * @deprecated As of release 2024.09.00, api.schema is not maintained any longer.
 * Users should migrate to using the new API structure in tech.pegasys.teku.api.response
 * and tech.pegasys.teku.api.request packages.
 * 
 * Migration Guide:
 * - For beacon node API responses, use classes from tech.pegasys.teku.api.response
 * - For validator API requests, use classes from tech.pegasys.teku.api.request
 * - For data structures, use the core classes from tech.pegasys.teku.spec.datastructures directly
 * 
 * Example migration:
 * Old: BeaconState from api.schema
 * New: BeaconState from spec.datastructures.state
 * 
 * For detailed migration examples and guidance, see the API documentation at:
 * https://docs.teku.consensys.net/development/api
 */
@Deprecated
package tech.pegasys.teku.api.schema;
