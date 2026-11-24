package com.forgeflow.dashboard;

public record DashboardSummary(long totalOrders, long pendingApproval, long inProgress, long completed,
                               long goodQuantity, long defectQuantity) {
}
