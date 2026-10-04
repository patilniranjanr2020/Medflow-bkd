package com.medflow.modules.pharmacy;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.datasource.url=jdbc:h2:mem:medflow-pharmacy;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "medflow.cache.provider=simple"
    })
class MedicationWorkflowTest {

  @Autowired
  private TestRestTemplate rest;

  @Test
  @DisplayName("Unauthenticated request to pharmacy catalogue returns 401 Unauthorized")
  void unauthenticatedAccessIsRejected() {
    var response = rest.getForEntity("/api/v1/pharmacy/medications", String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  @DisplayName("Creating medication fails validation when required fields are missing or invalid")
  void createValidationRejectsMissingOrInvalidFields() {
    var token = registerWorkspace("pharm-val@hospital.local", "Pharm Validation Hospital");
    
    // Blank name
    var blankNameResp = rest.exchange(
        "/api/v1/pharmacy/medications",
        HttpMethod.POST,
        authorized(token, Map.of(
            "name", "   ",
            "category", "Analgesics",
            "unitPrice", 10.0,
            "stockQuantity", 50,
            "reorderLevel", 10)),
        String.class);
    assertThat(blankNameResp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

    // Negative price
    var negPriceResp = rest.exchange(
        "/api/v1/pharmacy/medications",
        HttpMethod.POST,
        authorized(token, Map.of(
            "name", "Aspirin",
            "category", "Analgesics",
            "unitPrice", -5.0,
            "stockQuantity", 50,
            "reorderLevel", 10)),
        String.class);
    assertThat(negPriceResp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

    // Negative stock
    var negStockResp = rest.exchange(
        "/api/v1/pharmacy/medications",
        HttpMethod.POST,
        authorized(token, Map.of(
            "name", "Aspirin",
            "category", "Analgesics",
            "unitPrice", 5.0,
            "stockQuantity", -10,
            "reorderLevel", 10)),
        String.class);
    assertThat(negStockResp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("Complete end-to-end medication catalogue workflow: create, categories, search, filter, update, stock adjust, soft-delete, tenant isolation")
  void endToEndMedicationWorkflow() {
    var tokenA = registerWorkspace("pharm-admin-a@hospital-a.local", "Alpha Pharmacy Hospital");

    // 1. Create Medication 1 (Analgesics, healthy stock)
    var create1Resp = rest.exchange(
        "/api/v1/pharmacy/medications",
        HttpMethod.POST,
        authorized(tokenA, Map.of(
            "name", "Paracetamol 500mg",
            "category", "Analgesics",
            "unitPrice", 12.50,
            "stockQuantity", 100,
            "reorderLevel", 20,
            "expiryDate", LocalDate.now().plusYears(2).toString())),
        String.class);
    assertThat(create1Resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    int med1Id = JsonPath.read(create1Resp.getBody(), "$.data.id");
    assertThat(JsonPath.<String>read(create1Resp.getBody(), "$.data.name")).isEqualTo("Paracetamol 500mg");
    assertThat(JsonPath.<String>read(create1Resp.getBody(), "$.data.category")).isEqualTo("Analgesics");
    assertThat(JsonPath.<Boolean>read(create1Resp.getBody(), "$.data.lowStock")).isFalse();

    // 2. Create Medication 2 (Antibiotics, low stock)
    var create2Resp = rest.exchange(
        "/api/v1/pharmacy/medications",
        HttpMethod.POST,
        authorized(tokenA, Map.of(
            "name", "Amoxicillin 500mg",
            "category", "Antibiotics",
            "unitPrice", 45.00,
            "stockQuantity", 10,
            "reorderLevel", 15,
            "expiryDate", LocalDate.now().plusMonths(6).toString())),
        String.class);
    assertThat(create2Resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    int med2Id = JsonPath.read(create2Resp.getBody(), "$.data.id");
    assertThat(JsonPath.<Boolean>read(create2Resp.getBody(), "$.data.lowStock")).isTrue();

    // 3. Test distinct categories endpoint
    var categoriesResp = rest.exchange(
        "/api/v1/pharmacy/medications/categories",
        HttpMethod.GET,
        authorized(tokenA, null),
        String.class);
    assertThat(categoriesResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    List<String> categories = JsonPath.read(categoriesResp.getBody(), "$.data");
    assertThat(categories).contains("Analgesics", "Antibiotics");

    // 4. Search by query
    var searchResp = rest.exchange(
        "/api/v1/pharmacy/medications?query=Paracetamol",
        HttpMethod.GET,
        authorized(tokenA, null),
        String.class);
    assertThat(searchResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(JsonPath.<Integer>read(searchResp.getBody(), "$.data.totalElements")).isEqualTo(1);
    assertThat(JsonPath.<String>read(searchResp.getBody(), "$.data.content[0].name")).isEqualTo("Paracetamol 500mg");

    // 5. Filter by category
    var filterCategoryResp = rest.exchange(
        "/api/v1/pharmacy/medications?category=Antibiotics",
        HttpMethod.GET,
        authorized(tokenA, null),
        String.class);
    assertThat(filterCategoryResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(JsonPath.<Integer>read(filterCategoryResp.getBody(), "$.data.totalElements")).isEqualTo(1);
    assertThat(JsonPath.<String>read(filterCategoryResp.getBody(), "$.data.content[0].name")).isEqualTo("Amoxicillin 500mg");

    // 6. Filter by stockStatus: LOW_STOCK
    var lowStockResp = rest.exchange(
        "/api/v1/pharmacy/medications?stockStatus=LOW_STOCK",
        HttpMethod.GET,
        authorized(tokenA, null),
        String.class);
    assertThat(lowStockResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(JsonPath.<Integer>read(lowStockResp.getBody(), "$.data.totalElements")).isEqualTo(1);
    assertThat(JsonPath.<String>read(lowStockResp.getBody(), "$.data.content[0].name")).isEqualTo("Amoxicillin 500mg");

    // 7. Get by ID
    var getResp = rest.exchange(
        "/api/v1/pharmacy/medications/" + med1Id,
        HttpMethod.GET,
        authorized(tokenA, null),
        String.class);
    assertThat(getResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(JsonPath.<String>read(getResp.getBody(), "$.data.name")).isEqualTo("Paracetamol 500mg");

    // 8. Update medication details
    var updateResp = rest.exchange(
        "/api/v1/pharmacy/medications/" + med1Id,
        HttpMethod.PUT,
        authorized(tokenA, Map.of(
            "name", "Paracetamol Extra 650mg",
            "category", "Analgesics",
            "unitPrice", 16.00,
            "reorderLevel", 25,
            "expiryDate", LocalDate.now().plusYears(3).toString())),
        String.class);
    assertThat(updateResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(JsonPath.<String>read(updateResp.getBody(), "$.data.name")).isEqualTo("Paracetamol Extra 650mg");
    assertThat(JsonPath.<Double>read(updateResp.getBody(), "$.data.unitPrice")).isEqualTo(16.00);

    // 9. Stock adjustments (+10 restock)
    var restockResp = rest.exchange(
        "/api/v1/pharmacy/medications/" + med2Id + "/stock",
        HttpMethod.PATCH,
        authorized(tokenA, Map.of("delta", 10)),
        String.class);
    assertThat(restockResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(JsonPath.<Integer>read(restockResp.getBody(), "$.data.stockQuantity")).isEqualTo(20);
    assertThat(JsonPath.<Boolean>read(restockResp.getBody(), "$.data.lowStock")).isFalse();

    // 10. Soft-delete medication
    var deleteResp = rest.exchange(
        "/api/v1/pharmacy/medications/" + med1Id,
        HttpMethod.DELETE,
        authorized(tokenA, null),
        String.class);
    assertThat(deleteResp.getStatusCode()).isEqualTo(HttpStatus.OK);

    // Verify deleted medication is not returned in GET
    var verifyGetResp = rest.exchange(
        "/api/v1/pharmacy/medications/" + med1Id,
        HttpMethod.GET,
        authorized(tokenA, null),
        String.class);
    assertThat(verifyGetResp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

    // Verify deleted medication is omitted from search results
    var verifySearchResp = rest.exchange(
        "/api/v1/pharmacy/medications?query=Paracetamol",
        HttpMethod.GET,
        authorized(tokenA, null),
        String.class);
    assertThat(verifySearchResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(JsonPath.<Integer>read(verifySearchResp.getBody(), "$.data.totalElements")).isEqualTo(0);

    // 11. Multi-tenant isolation test: User B in another hospital cannot see User A's medicine
    var tokenB = registerWorkspace("pharm-admin-b@hospital-b.local", "Beta Pharmacy Hospital");
    var getCrossTenant = rest.exchange(
        "/api/v1/pharmacy/medications/" + med2Id,
        HttpMethod.GET,
        authorized(tokenB, null),
        String.class);
    assertThat(getCrossTenant.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  private String registerWorkspace(String email, String hospitalName) {
    var response = rest.postForEntity("/api/v1/auth/register", json(Map.of(
        "fullName", "Admin User",
        "email", email,
        "password", "changeit-123",
        "confirmPassword", "changeit-123",
        "hospitalName", hospitalName)), String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return JsonPath.read(response.getBody(), "$.data.accessToken");
  }

  private HttpEntity<Object> authorized(String token, Object body) {
    var headers = new HttpHeaders();
    headers.setBearerAuth(token);
    if (body != null) {
      headers.setContentType(MediaType.APPLICATION_JSON);
    }
    return new HttpEntity<>(body, headers);
  }

  private HttpEntity<Map<String, Object>> json(Map<String, Object> body) {
    var headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return new HttpEntity<>(body, headers);
  }
}
