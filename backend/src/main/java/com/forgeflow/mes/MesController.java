package com.forgeflow.mes;

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
@RequestMapping("/api/mes/work-orders")
public class MesController {
    private final MesService mesService;

    public MesController(MesService mesService) {
        this.mesService = mesService;
    }

    @GetMapping
    public List<MesWorkOrderView> workOrders() {
        return mesService.workOrders();
    }

    @PostMapping("/{orderId}/start")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void start(@PathVariable long orderId) {
        mesService.start(orderId);
    }

    @PostMapping("/{orderId}/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductionReportView report(@PathVariable long orderId, @Valid @RequestBody ProductionReportRequest request,
                                       Authentication authentication) {
        return mesService.report(orderId, request, authentication.getName());
    }
}
