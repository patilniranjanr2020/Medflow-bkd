package com.medflow.modules.laboratory;

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
        "spring.datasource.url=jdbc:h2:mem:medflow-lab;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "medflow.cache.provider=simple"
    })
class LabOrderWorkflowTest {

  @Autowired
  private TestRestTemplate rest;

  @Test
  @DisplayName("Unauthenticated request to laboratory queue returns 401 Unauthorized")
  void unauthenticatedAccessIsRejected() {
    var response = rest.getForEntity("/api/v1/lab-orders", String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  @DisplayName("Creating lab order fails validation when required fields are missing")
  void createValidationRejectsMissingFields() {
    var token = registerWorkspace("lab-val@hospital.local", "Lab Validation Hospital");
    var response = rest.exchange(
        "/api/v1/lab-orders",
        HttpMethod.POST,
        authorized(token, Map.of("testName", "   ")),
        String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("Complete end-to-end lab order workflow: create, search, update, progress, delete, tenant isolation")
  void endToEndLabOrderWorkflow() {
    var tokenA = registerWorkspace("lab-admin-a@hospital-a.local", "Alpha Lab Hospital");
    var doctorIdA = createDoctor(tokenA, "Rohan", "Mehta", "rohan@hospital-a.local", "Pathology");
    var patientIdA = createPatient(tokenA, "Ananya", "Sen", "1994-08-20", "FEMALE", "ananya@test.local", "B+");

    // 1. Create order
    var createResponse = rest.exchange(
        "/api/v1/lab-orders",
        HttpMethod.POST,
        authorized(tokenA, Map.of(
            "patientId", patientIdA,
            "doctorId", doctorIdA,
            "testName", "Complete Blood Count",
            "priority", "ROUTINE")),
        String.class);
    assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    int orderId = JsonPath.read(createResponse.getBody(), "$.data.id");
    assertThat(JsonPath.<String>read(createResponse.getBody(), "$.data.testName")).isEqualTo("Complete Blood Count");
    assertThat(JsonPath.<String>read(createResponse.getBody(), "$.data.patientName")).isEqualTo("Ananya Sen");
    assertThat(JsonPath.<String>read(createResponse.getBody(), "$.data.doctorName")).isEqualTo("Rohan Mehta");
    assertThat(JsonPath.<String>read(createResponse.getBody(), "$.data.status")).isEqualTo("ORDERED");

    // 2. Search by test name
    var searchTestResponse = rest.exchange(
        "/api/v1/lab-orders?query=Complete",
        HttpMethod.GET,
        authorized(tokenA, null),
        String.class);
    assertThat(searchTestResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(JsonPath.<Integer>read(searchTestResponse.getBody(), "$.data.totalElements")).isGreaterThanOrEqualTo(1);

    // 3. Search by patient name
    var searchPatientResponse = rest.exchange(
        "/api/v1/lab-orders?query=Ananya",
        HttpMethod.GET,
        authorized(tokenA, null),
        String.class);
    assertThat(searchPatientResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(JsonPath.<Integer>read(searchPatientResponse.getBody(), "$.data.totalElements")).isGreaterThanOrEqualTo(1);

    // 4. Update details
    var updateResponse = rest.exchange(
        "/api/v1/lab-orders/" + orderId,
        HttpMethod.PUT,
        authorized(tokenA, Map.of(
            "testName", "Complete Blood Count (CBC with Differential)",
            "priority", "URGENT",
            "doctorId", doctorIdA)),
        String.class);
    assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(JsonPath.<String>read(updateResponse.getBody(), "$.data.testName"))
        .isEqualTo("Complete Blood Count (CBC with Differential)");
    assertThat(JsonPath.<String>read(updateResponse.getBody(), "$.data.priority")).isEqualTo("URGENT");

    // 5. Start processing
    var startResponse = rest.exchange(
        "/api/v1/lab-orders/" + orderId + "/start",
        HttpMethod.PATCH,
        authorized(tokenA, null),
        String.class);
    assertThat(startResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(JsonPath.<String>read(startResponse.getBody(), "$.data.status")).isEqualTo("IN_PROGRESS");

    // 6. Complete / record result
    var completeResponse = rest.exchange(
        "/api/v1/lab-orders/" + orderId + "/complete",
        HttpMethod.PATCH,
        authorized(tokenA, Map.of("resultSummary", "Hemoglobin 13.8 g/dL, WBC normal")),
        String.class);
    assertThat(completeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(JsonPath.<String>read(completeResponse.getBody(), "$.data.status")).isEqualTo("COMPLETED");
    assertThat(JsonPath.<String>read(completeResponse.getBody(), "$.data.resultSummary"))
        .isEqualTo("Hemoglobin 13.8 g/dL, WBC normal");

    // 7. Signed-off protection: attempting to complete already completed order throws error
    var invalidComplete = rest.exchange(
        "/api/v1/lab-orders/" + orderId + "/complete",
        HttpMethod.PATCH,
        authorized(tokenA, Map.of("resultSummary", "Tampered result")),
        String.class);
    assertThat(invalidComplete.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

    // 8. Tenant isolation: Workspace B cannot view or delete order from Workspace A
    var tokenB = registerWorkspace("lab-admin-b@hospital-b.local", "Beta Lab Hospital");
    var crossGet = rest.exchange(
        "/api/v1/lab-orders/" + orderId,
        HttpMethod.GET,
        authorized(tokenB, null),
        String.class);
    assertThat(crossGet.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

    var crossDelete = rest.exchange(
        "/api/v1/lab-orders/" + orderId,
        HttpMethod.DELETE,
        authorized(tokenB, null),
        String.class);
    assertThat(crossDelete.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

    // 9. Soft delete
    var deleteResponse = rest.exchange(
        "/api/v1/lab-orders/" + orderId,
        HttpMethod.DELETE,
        authorized(tokenA, null),
        String.class);
    assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

    // Verify order is no longer returned
    var verifyGet = rest.exchange(
        "/api/v1/lab-orders/" + orderId,
        HttpMethod.GET,
        authorized(tokenA, null),
        String.class);
    assertThat(verifyGet.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
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

  private int createDoctor(String token, String firstName, String lastName, String email, String specialty) {
    var response = rest.exchange("/api/v1/doctors", HttpMethod.POST, authorized(token, Map.of(
        "firstName", firstName,
        "lastName", lastName,
        "email", email,
        "phone", "+91 98765 00099",
        "specialty", specialty,
        "registrationNumber", "MCI-2026-" + System.currentTimeMillis() % 100000,
        "yearsOfExperience", 8,
        "consultationFee", 600)), String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return JsonPath.read(response.getBody(), "$.data.id");
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
