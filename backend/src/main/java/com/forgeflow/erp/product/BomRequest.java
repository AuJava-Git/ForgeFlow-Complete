package com.forgeflow.erp.product;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record BomRequest(@NotEmpty List<@Valid BomLineRequest> items) {
}
