package io.github.dimasnurfauzi.invoicing.customer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CustomerServiceTests {

  @Test
  void likePatternMatchesEverythingForBlankQuery() {
    assertThat(CustomerService.likePattern(null)).isEqualTo("%");
    assertThat(CustomerService.likePattern("  ")).isEqualTo("%");
  }

  @Test
  void likePatternLowercasesAndEscapesWildcards() {
    assertThat(CustomerService.likePattern(" Toko ")).isEqualTo("%toko%");
    assertThat(CustomerService.likePattern("100%_!")).isEqualTo("%100!%!_!!%");
  }
}
