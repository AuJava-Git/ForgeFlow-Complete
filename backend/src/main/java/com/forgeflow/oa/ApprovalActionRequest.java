package com.forgeflow.oa;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ApprovalActionRequest(@NotNull Boolean approved, @Size(max = 500) String comment) {
}
