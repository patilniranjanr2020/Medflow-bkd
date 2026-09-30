package com.medflow.modules.patients;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
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
        "spring.datasource.url=jdbc:h2:mem:medflow-profile;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "medflow.cache.provider=simple"
    })
class PatientProfileAccessTest {

  @Autowired
  private TestRestTemplate rest;

  @Test
  @DisplayName("Unauthenticated request to patient profile returns 401 Unauthorized")
  void unauthenticatedAccessIsRejected() {
    var response = rest.getForEntity("/api/v1/patients/1", String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  @DisplayName("Fetching patient profile returns complete data and records an auditable PATIENT_VIEWED event")
  void profileAccessReturnsDataAndRecordsAuditEvent() {
    // 1. Create Workspace A
    var tokenA = registerWorkspace("admin_a@hospital-a.local", "Hospital Alpha");

    // 2. Register Patient in Workspace A
    var patientId = createPatient(tokenA, "Aarav", "Sharma", "1988-05-15", "MALE", "aarav@test.local", "A+");

    // 3. Fetch Patient Profile as Workspace A admin
    var getResponse = rest.exchange(
        "/api/v1/patients/" + patientId,
        HttpMethod.GET,
        authorized(tokenA, null),
        String.class);

    assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(JsonPath.<String>read(getResponse.getBody(), "$.data.firstName")).isEqualTo("Aarav");
    assertThat(JsonPath.<String>read(getResponse.getBody(), "$.data.lastName")).isEqualTo("Sharma");
    assertThat(JsonPath.<String>read(getResponse.getBody(), "$.data.gender")).isEqualTo("MALE");
    assertThat(JsonPath.<String>read(getResponse.getBody(), "$.data.bloodGroup")).isEqualTo("A+");
    assertThat(JsonPath.<Integer>read(getResponse.getBody(), "$.data.id")).isEqualTo(patientId);

    // 4. Verify medical-history, accounts, reports endpoints return 200
    var historyResponse = rest.exchange(
        "/api/v1/patients/" + patientId + "/medical-history",
        HttpMethod.GET,
        authorized(tokenA, null),
        String.class);
    assertThat(historyResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

    var accountsResponse = rest.exchange(
        "/api/v1/patients/" + patientId + "/accounts",
        HttpMethod.GET,
        authorized(tokenA, null),
        String.class);
    assertThat(accountsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

    var reportsResponse = rest.exchange(
        "/api/v1/patients/" + patientId + "/reports",
        HttpMethod.GET,
        authorized(tokenA, null),
        String.class);
    assertThat(reportsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

    // 5. Verify audit log has PATIENT_VIEWED entry without sensitive data
    var auditResponse = rest.exchange(
        "/api/v1/audit-logs?entityType=patient",
        HttpMethod.GET,
        authorized(tokenA, null),
        String.class);
    assertThat(auditResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

    List<String> actions = JsonPath.read(auditResponse.getBody(), "$.data.content[*].action");
    assertThat(actions).contains("PATIENT_VIEWED");

    // Ensure audit metadata does not leak sensitive information
    List<Map<String, Object>> entries = JsonPath.read(auditResponse.getBody(), "$.data.content[?(@.action == 'PATIENT_VIEWED')]");
    assertThat(entries).isNotEmpty();
    for (var entry : entries) {
      assertThat(entry.get("metadataJson")).isNull();
    }
  }

  @Test
  @DisplayName("Tenant isolation: Workspace B cannot access patient belonging to Workspace A")
  void tenantIsolationPreventsCrossHospitalAccess() {
    // 1. Create Workspace A and a patient
    var tokenA = registerWorkspace("admin_alpha@hospital-alpha.local", "Hospital Alpha");
    var patientIdA = createPatient(tokenA, "Rohan", "Verma", "1995-10-20", "MALE", "rohan@test.local", "B+");

    // 2. Create Workspace B
    var tokenB = registerWorkspace("admin_beta@hospital-beta.local", "Hospital Beta");

    // 3. Workspace B attempts to access Workspace A's patient
    var crossTenantResponse = rest.exchange(
        "/api/v1/patients/" + patientIdA,
        HttpMethod.GET,
        authorized(tokenB, null),
        String.class);

    // Must return 404 (not found in this tenant) to prevent cross-tenant enumeration and data leakage
    assertThat(crossTenantResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

    // Sub-resources must also enforce tenant isolation
    var crossHistory = rest.exchange(
        "/api/v1/patients/" + patientIdA + "/medical-history",
        HttpMethod.GET,
        authorized(tokenB, null),
        String.class);
    assertThat(crossHistory.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

    var crossAccounts = rest.exchange(
        "/api/v1/patients/" + patientIdA + "/accounts",
        HttpMethod.GET,
        authorized(tokenB, null),
        String.class);
    assertThat(crossAccounts.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

    var crossReports = rest.exchange(
        "/api/v1/patients/" + patientIdA + "/reports",
        HttpMethod.GET,
        authorized(tokenB, null),
        String.class);
    assertThat(crossReports.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
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

  private int createPatient(String token, String firstName, String lastName, String dob, String gender, String email, String bloodGroup) {
    var response = rest.exchange("/api/v1/patients", HttpMethod.POST, authorized(token, Map.of(
        "firstName", firstName,
        "lastName", lastName,
        "dateOfBirth", dob,
        "gender", gender,
        "email", email,
        "bloodGroup", bloodGroup)), String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return JsonPath.read(response.getBody(), "$.data.id");
  }

  private HttpEntity<Map<String, Object>> authorized(String token, Map<String, Object> body) {
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
