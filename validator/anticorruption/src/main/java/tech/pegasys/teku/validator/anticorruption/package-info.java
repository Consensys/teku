/**
 * This package provides an anti-corruption layer which isolates the new separate-validator-client
 * from the old combined beacon chain and validator approach, but allows mapping some of the old
 * functionality through to the new world in a limited way.
 *
 * <p>The intention is that this package will eventually be replaced by functionality built
 * specifically for the new-world so the old approach can be completely removed and the validator
 * run standalone.
 */
package tech.pegasys.teku.validator.anticorruption;
