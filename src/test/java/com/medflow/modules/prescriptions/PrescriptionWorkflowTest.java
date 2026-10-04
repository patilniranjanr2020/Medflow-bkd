package com.medflow.modules.prescriptions;

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
        "spring.datasource.url=jdbc:h2:mem:medflow-prescriptions;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "medflow.cache.provider=simple"
    })
class PrescriptionWorkflowTest {

  @Autowired
  private TestRestTemplate rest;

  @Test
  @DisplayName("Unauthenticated request to prescriptions returns 401 Unauthorized")
  void unauthenticatedAccessIsRejected() {
    var response = rest.getForEntity("/api/v1/prescriptions", String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  @DisplayName("Creating prescription fails validation when required fields are missing")
  void createValidationRejectsMissingFields() {
    var token = registerWorkspace("rx-val@hospital.local", "Rx Validation Hospital");
    var response = rest.exchange(
        "/api/v1/prescriptions",
        HttpMethod.POST,
        authorized(token, Map.of("diagnosis", "Flu")),
        String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("Creating prescription fails when doctor is not registered in the hospital")
  void createRejectsUnregisteredDoctor() {
    var token = registerWorkspace("rx-doc-val@hospital.local", "Rx Doctor Hospital");
    var patientId = createPatient(token, "Maya", "Verma", "1990-05-15", "FEMALE", "maya@test.local", "O+");

    var response = rest.exchange(
        "/api/v1/prescriptions",
        HttpMethod.POST,
        authorized(token, Map.of(
            "patientId", patientId,
            "doctorId", 999999L,
            "diagnosis", "Viral fever",
            "digitallySigned", false,
            "medicines", List.of(Map.of(
                "medicationName", "Paracetamol 500mg",
                "dosage", "1 tab",
                "frequency", "TDS",
                "durationDays", 3
            )))),
        String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).contains("Doctor not found");
  }

  @Test
  @DisplayName("Complete end-to-end prescription workflow: create draft, read, search, update, delete")
  void endToEndDraftPrescriptionWorkflow() {
    var token = registerWorkspace("rx-flow@hospital.local", "Rx Workflow Hospital");
    var doctorId = createDoctor(token, "Aditi", "Rao", "aditi@rx-flow.local", "General Medicine");
    var patientId = createPatient(token, "Rahul", "Dravid", "1980-01-11", "MALE", "rahul@rx-flow.local", "A+");

    // 1. Create draft prescription (digitallySigned = false)
    var createResponse = rest.exchange(
        "/api/v1/prescriptions",
        HttpMethod.POST,
        authorized(token, Map.of(
            "patientId", patientId,
            "doctorId", doctorId,
            "diagnosis", "Upper respiratory tract infection",
            "digitallySigned", false,
            "medicines", List.of(
                Map.of(
                    "medicationName", "Amoxicillin 500mg",
                    "dosage", "1 cap",
                    "frequency", "Twice daily",
                    "durationDays", 5,
                    "instructions", "Take after meals"
                )
            ))),
        String.class);

    assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    int prescriptionId = JsonPath.read(createResponse.getBody(), "$.data.id");
    assertThat(JsonPath.<Boolean>read(createResponse.getBody(), "$.data.digitallySigned")).isFalse();
    assertThat(JsonPath.<String>read(createResponse.getBody(), "$.data.status")).isEqualTo("ACTIVE");
    assertThat(JsonPath.<String>read(createResponse.getBody(), "$.data.patientName")).isEqualTo("Rahul Dravid");
    assertThat(JsonPath.<String>read(createResponse.getBody(), "$.data.doctorName")).isEqualTo("Aditi Rao");
    assertThat(JsonPath.<List<?>>read(createResponse.getBody(), "$.data.medicines")).hasSize(1);

    // 2. Fetch by ID
    var getResponse = rest.exchange(
        "/api/v1/prescriptions/" + prescriptionId,
        HttpMethod.GET,
        authorized(token, null),
        String.class);
    assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(JsonPath.<Integer>read(getResponse.getBody(), "$.data.id")).isEqualTo(prescriptionId);

    // 3. Search by patient
    var searchResponse = rest.exchange(
        "/api/v1/prescriptions?patientId=" + patientId,
        HttpMethod.GET,
        authorized(token, null),
        String.class);
    assertThat(searchResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(JsonPath.<Integer>read(searchResponse.getBody(), "$.data.totalElements")).isGreaterThanOrEqualTo(1);

    // 4. Update draft prescription
    var updateResponse = rest.exchange(
        "/api/v1/prescriptions/" + prescriptionId,
        HttpMethod.PUT,
        authorized(token, Map.of(
            "patientId", patientId,
            "doctorId", doctorId,
            "diagnosis", "Acute Bronchitis",
            "digitallySigned", false,
            "medicines", List.of(
                Map.of(
                    "medicationName", "Amoxicillin 500mg",
                    "dosage", "1 cap",
                    "frequency", "TDS",
                    "durationDays", 7,
                    "instructions", "After meals"
                ),
                Map.of(
                    "medicationName", "Cetirizine 10mg",
                    "dosage", "1 tab",
                    "frequency", "Once daily at night",
                    "durationDays", 5,
                    "instructions", "Before bed"
                )
            ))),
        String.class);

    assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(JsonPath.<String>read(updateResponse.getBody(), "$.data.diagnosis")).isEqualTo("Acute Bronchitis");
    assertThat(JsonPath.<List<?>>read(updateResponse.getBody(), "$.data.medicines")).hasSize(2);

    // 5. Delete draft prescription
    var deleteResponse = rest.exchange(
        "/api/v1/prescriptions/" + prescriptionId,
        HttpMethod.DELETE,
        authorized(token, null),
        String.class);
    assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

    // 6. Verify deleted prescription is gone
    var verifyResponse = rest.exchange(
        "/api/v1/prescriptions/" + prescriptionId,
        HttpMethod.GET,
        authorized(token, null),
        String.class);
    assertThat(verifyResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  @DisplayName("Digitally signed prescriptions cannot be edited or deleted")
  void signedPrescriptionProtectionRules() {
    var token = registerWorkspace("rx-signed@hospital.local", "Rx Signed Hospital");
    var doctorId = createDoctor(token, "Vikram", "Seth", "vikram@signed.local", "Cardiology");
    var patientId = createPatient(token, "Kavita", "Krishnan", "1985-04-12", "FEMALE", "kavita@signed.local", "B+");

    // 1. Create digitally signed prescription
    var createResponse = rest.exchange(
        "/api/v1/prescriptions",
        HttpMethod.POST,
        authorized(token, Map.of(
            "patientId", patientId,
            "doctorId", doctorId,
            "diagnosis", "Hypertension Stage 1",
            "digitallySigned", true,
            "medicines", List.of(
                Map.of(
                    "medicationName", "Amlodipine 5mg",
                    "dosage", "1 tab",
                    "frequency", "Once daily morning",
                    "durationDays", 30
                )
            ))),
        String.class);

    assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    int prescriptionId = JsonPath.read(createResponse.getBody(), "$.data.id");
    assertThat(JsonPath.<Boolean>read(createResponse.getBody(), "$.data.digitallySigned")).isTrue();

    // 2. Attempting to update signed prescription should fail with BusinessRuleViolation (400)
    var updateResponse = rest.exchange(
        "/api/v1/prescriptions/" + prescriptionId,
        HttpMethod.PUT,
        authorized(token, Map.of(
            "patientId", patientId,
            "doctorId", doctorId,
            "diagnosis", "Hypertension Stage 2",
            "digitallySigned", true,
            "medicines", List.of(
                Map.of(
                    "medicationName", "Amlodipine 10mg",
                    "dosage", "1 tab",
                    "frequency", "Once daily",
                    "durationDays", 30
                )
            ))),
        String.class);

    assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(updateResponse.getBody()).contains("A digitally signed prescription cannot be modified");

    // 3. Attempting to delete signed prescription should fail with BusinessRuleViolation (422)
    var deleteResponse = rest.exchange(
        "/api/v1/prescriptions/" + prescriptionId,
        HttpMethod.DELETE,
        authorized(token, null),
        String.class);

    assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(deleteResponse.getBody()).contains("A digitally signed prescription cannot be deleted");

    // 4. Lifecycle transitions (complete / cancel) work on active signed prescriptions
    var completeResponse = rest.exchange(
        "/api/v1/prescriptions/" + prescriptionId + "/complete",
        HttpMethod.PATCH,
        authorized(token, null),
        String.class);
    assertThat(completeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(JsonPath.<String>read(completeResponse.getBody(), "$.data.status")).isEqualTo("COMPLETED");

    // Attempting to cancel already completed prescription fails
    var cancelCompleted = rest.exchange(
        "/api/v1/prescriptions/" + prescriptionId + "/cancel",
        HttpMethod.PATCH,
        authorized(token, null),
        String.class);
    assertThat(cancelCompleted.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(cancelCompleted.getBody()).contains("Only active prescriptions can be cancelled");
  }

  @Test
  @DisplayName("Tenant isolation: cannot access or modify prescriptions or doctors across hospitals")
  void tenantIsolation() {
    var tokenA = registerWorkspace("rx-tenant-a@hospital-a.local", "Hospital Alpha");
    var doctorIdA = createDoctor(tokenA, "Doctor", "Alpha", "doc.a@hospital-a.local", "General");
    var patientIdA = createPatient(tokenA, "Patient", "Alpha", "1990-01-01", "MALE", "p.a@hospital-a.local", "O+");

    var tokenB = registerWorkspace("rx-tenant-b@hospital-b.local", "Hospital Beta");
    var doctorIdB = createDoctor(tokenB, "Doctor", "Beta", "doc.b@hospital-b.local", "Pediatrics");
    var patientIdB = createPatient(tokenB, "Patient", "Beta", "1992-02-02", "FEMALE", "p.b@hospital-b.local", "AB+");

    // Hospital A creates a draft prescription
    var createResponse = rest.exchange(
        "/api/v1/prescriptions",
        HttpMethod.POST,
        authorized(tokenA, Map.of(
            "patientId", patientIdA,
            "doctorId", doctorIdA,
            "diagnosis", "Common Cold",
            "digitallySigned", false,
            "medicines", List.of(Map.of(
                "medicationName", "Paracetamol 500mg",
                "dosage", "1 tab",
                "frequency", "TDS",
                "durationDays", 3
            )))),
        String.class);
    int prescriptionIdA = JsonPath.read(createResponse.getBody(), "$.data.id");

    // Hospital B cannot view Hospital A's prescription
    var crossView = rest.exchange(
        "/api/v1/prescriptions/" + prescriptionIdA,
        HttpMethod.GET,
        authorized(tokenB, null),
        String.class);
    assertThat(crossView.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

    // Hospital B cannot delete Hospital A's prescription
    var crossDelete = rest.exchange(
        "/api/v1/prescriptions/" + prescriptionIdA,
        HttpMethod.DELETE,
        authorized(tokenB, null),
        String.class);
    assertThat(crossDelete.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

    // Hospital A cannot assign Doctor B from Hospital B
    var crossDoctorAssign = rest.exchange(
        "/api/v1/prescriptions",
        HttpMethod.POST,
        authorized(tokenA, Map.of(
            "patientId", patientIdA,
            "doctorId", doctorIdB,
            "diagnosis", "Invalid doctor test",
            "digitallySigned", false,
            "medicines", List.of(Map.of(
                "medicationName", "Paracetamol 500mg",
                "dosage", "1 tab",
                "frequency", "TDS",
                "durationDays", 3
            )))),
        String.class);
    assertThat(crossDoctorAssign.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(crossDoctorAssign.getBody()).contains("Doctor not found");
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
        "registrationNumber", "MCI-RX-" + System.currentTimeMillis() % 100000 + "-" + Math.round(Math.random() * 1000),
        "yearsOfExperience", 5,
        "consultationFee", 500)), String.class);
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
