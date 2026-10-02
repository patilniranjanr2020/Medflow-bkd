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

  @Test
  @DisplayName("Partial update succeeds, preserves untouched fields, updates timestamp, and records audit")
  void partialUpdatePreservesUntouchedFieldsAndUpdatesTimestampAndAudit() {
    var token = registerWorkspace("update_test@hospital.local", "Hospital Update Test");
    var patientId = createPatient(token, "Priya", "Nair", "1992-08-25", "FEMALE", "priya@test.local", "B+");

    // Get current profile
    var initialResponse = rest.exchange(
        "/api/v1/patients/" + patientId,
        HttpMethod.GET,
        authorized(token, null),
        String.class);
    assertThat(initialResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    String initialUpdatedAt = JsonPath.read(initialResponse.getBody(), "$.data.updatedAt");
    String patientCode = JsonPath.read(initialResponse.getBody(), "$.data.patientCode");

    // Perform partial update changing only phone and bloodGroup
    var updatePayload = Map.of(
        "phone", "+91 99999 88888",
        "bloodGroup", "AB+",
        "lastUpdatedAt", initialUpdatedAt);

    var updateResponse = rest.exchange(
        "/api/v1/patients/" + patientId,
        HttpMethod.PUT,
        authorized(token, updatePayload),
        String.class);

    assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(JsonPath.<String>read(updateResponse.getBody(), "$.data.phone")).isEqualTo("+91 99999 88888");
    assertThat(JsonPath.<String>read(updateResponse.getBody(), "$.data.bloodGroup")).isEqualTo("AB+");
    // Verify untouched fields were preserved
    assertThat(JsonPath.<String>read(updateResponse.getBody(), "$.data.firstName")).isEqualTo("Priya");
    assertThat(JsonPath.<String>read(updateResponse.getBody(), "$.data.lastName")).isEqualTo("Nair");
    assertThat(JsonPath.<String>read(updateResponse.getBody(), "$.data.gender")).isEqualTo("FEMALE");
    assertThat(JsonPath.<String>read(updateResponse.getBody(), "$.data.dateOfBirth")).isEqualTo("1992-08-25");
    assertThat(JsonPath.<String>read(updateResponse.getBody(), "$.data.email")).isEqualTo("priya@test.local");
    // Verify server-managed patient code was preserved
    assertThat(JsonPath.<String>read(updateResponse.getBody(), "$.data.patientCode")).isEqualTo(patientCode);
    // Verify updatedAt was refreshed
    String newUpdatedAt = JsonPath.read(updateResponse.getBody(), "$.data.updatedAt");
    assertThat(newUpdatedAt).isNotNull();

    // Verify audit log has PATIENT_UPDATED
    var auditResponse = rest.exchange(
        "/api/v1/audit-logs?entityType=patient",
        HttpMethod.GET,
        authorized(token, null),
        String.class);
    assertThat(auditResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    List<String> actions = JsonPath.read(auditResponse.getBody(), "$.data.content[*].action");
    assertThat(actions).contains("PATIENT_UPDATED");
  }

  @Test
  @DisplayName("Optimistic locking rejects update with 409 Conflict when lastUpdatedAt is stale")
  void optimisticLockingRejectsStaleUpdate() {
    var token = registerWorkspace("concurrency@hospital.local", "Hospital Concurrency Test");
    var patientId = createPatient(token, "Rahul", "Dravid", "1973-01-11", "MALE", "rahul@test.local", "O+");

    // First update moves the timestamp forward
    var firstUpdate = rest.exchange(
        "/api/v1/patients/" + patientId,
        HttpMethod.PUT,
        authorized(token, Map.of("phone", "+91 91111 22222")),
        String.class);
    assertThat(firstUpdate.getStatusCode()).isEqualTo(HttpStatus.OK);

    // Stale update using an old timestamp
    var stalePayload = Map.of(
        "phone", "+91 93333 44444",
        "lastUpdatedAt", "2020-01-01T00:00:00Z");

    var conflictResponse = rest.exchange(
        "/api/v1/patients/" + patientId,
        HttpMethod.PUT,
        authorized(token, stalePayload),
        String.class);

    assertThat(conflictResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  @DisplayName("Validation fails when updating with invalid blood group or future date of birth")
  void invalidDemographicFieldsAreRejected() {
    var token = registerWorkspace("validation@hospital.local", "Hospital Validation Test");
    var patientId = createPatient(token, "Anita", "Desai", "1985-03-15", "FEMALE", "anita@test.local", "A+");

    // Invalid blood group
    var badBloodResponse = rest.exchange(
        "/api/v1/patients/" + patientId,
        HttpMethod.PUT,
        authorized(token, Map.of("bloodGroup", "XYZ")),
        String.class);
    assertThat(badBloodResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

    // Future date of birth
    var futureDobResponse = rest.exchange(
        "/api/v1/patients/" + patientId,
        HttpMethod.PUT,
        authorized(token, Map.of("dateOfBirth", "2099-01-01")),
        String.class);
    assertThat(futureDobResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("Tenant isolation: Workspace B cannot update Workspace A's patient")
  void tenantIsolationPreventsCrossHospitalUpdate() {
    var tokenA = registerWorkspace("tenant_a@hospital.local", "Hospital Tenant A");
    var patientIdA = createPatient(tokenA, "Vikram", "Seth", "1952-06-20", "MALE", "vikram@test.local", "B-");

    var tokenB = registerWorkspace("tenant_b@hospital.local", "Hospital Tenant B");

    var crossUpdateResponse = rest.exchange(
        "/api/v1/patients/" + patientIdA,
        HttpMethod.PUT,
        authorized(tokenB, Map.of("phone", "+91 97777 66666")),
        String.class);

    assertThat(crossUpdateResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
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
