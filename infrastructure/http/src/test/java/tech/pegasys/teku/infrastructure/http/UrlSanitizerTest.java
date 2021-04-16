package tech.pegasys.teku.infrastructure.http;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UrlSanitizerTest {
  @Test
  void shouldRemoveBasicCredentialsFromUrl() {
    final String input = "http://user:pass@localhost:2993/some%20path/b/c/?foo=bar#1234";
    final String result = UrlSanitizer.sanitizePotentialUrl(input);
    assertThat(result).isEqualTo("http://localhost:2993/some%20path/b/c/?foo=bar#1234");
  }

  @Test
  void shouldRemoveBasicCredentialsFromUrlWithUnusualScheme() {
    final String input = "yasf://user:pass@localhost:2993/some%20path/b/c/?foo=bar#1234";
    final String result = UrlSanitizer.sanitizePotentialUrl(input);
    assertThat(result).isEqualTo("yasf://localhost:2993/some%20path/b/c/?foo=bar#1234");
  }

  @Test
  void shouldNotModifyUrlWithNoCredentials() {
    final String input = "http://localhost:2993/some%20path/b/c/?foo=bar#1234";
    final String result = UrlSanitizer.sanitizePotentialUrl(input);
    assertThat(result).isEqualTo("http://localhost:2993/some%20path/b/c/?foo=bar#1234");
  }

  @Test
  void shouldNotModifyStringThatIsNotAUrl() {
    final String input = "user:passlocalhost:2993/some%20path/b/c/?foo=bar#1234";
    final String result = UrlSanitizer.sanitizePotentialUrl(input);
    assertThat(result).isEqualTo("user:passlocalhost:2993/some%20path/b/c/?foo=bar#1234");
  }
}
