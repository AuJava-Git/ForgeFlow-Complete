package com.forgeflow.oa;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/oa/production-orders")
public class ApprovalController {
    private final ApprovalService approvalService;

    public ApprovalController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @PostMapping("/{id}/submit")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void submit(@PathVariable long id, Authentication authentication) {
        approvalService.submit(id, authentication.getName());
    }

    @PostMapping("/{id}/decision")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void decide(@PathVariable long id, @Valid @RequestBody ApprovalActionRequest request, Authentication authentication) {
        approvalService.decide(id, request.approved(), request.comment(), authentication.getName());
    }

    @GetMapping("/pending")
    public List<ApprovalOrderView> pending() {
        return approvalService.pending();
    }

    @GetMapping("/{id}/history")
    public List<ApprovalHistoryView> history(@PathVariable long id) {
        return approvalService.history(id);
    }
}
