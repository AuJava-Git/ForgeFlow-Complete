package com.forgeflow.oa;

import java.time.LocalDateTime;

public record ApprovalHistoryView(Long id, String action, String actor, String comment, LocalDateTime createdAt) {
}
