package com.medflow.modules.settings.api.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record UpdateProfileRequest(
    @NotNull int version,
    @NotEmpty List<FieldStateRequest> fields) {

  public record FieldStateRequest(
      @NotNull String fieldKey,
      @NotNull String state) {  // REQUIRED, OPTIONAL, HIDDEN
  }
}
