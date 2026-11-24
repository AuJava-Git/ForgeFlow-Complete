package com.forgeflow.inventory;

import com.forgeflow.inventory.InventoryDtos.InventoryBalanceView;
import com.forgeflow.inventory.InventoryDtos.InventoryReceiptRequest;
import com.forgeflow.inventory.InventoryDtos.MaterialRequirementView;
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
@RequestMapping("/api/inventory")
public class InventoryController {
    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/balances")
    public List<InventoryBalanceView> balances() {
        return inventoryService.balances();
    }

    @PostMapping("/receipts")
    @ResponseStatus(HttpStatus.CREATED)
    public void receiveOpeningStock(@Valid @RequestBody InventoryReceiptRequest request, Authentication authentication) {
        inventoryService.receiveOpeningStock(request, authentication.getName());
    }

    @GetMapping("/production-orders/{orderId}/requirements")
    public List<MaterialRequirementView> requirements(@PathVariable long orderId) {
        return inventoryService.requirements(orderId);
    }

    @PostMapping("/production-orders/{orderId}/reserve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reserve(@PathVariable long orderId, Authentication authentication) {
        inventoryService.reserve(orderId, authentication.getName());
    }

    @PostMapping("/production-orders/{orderId}/issue")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void issue(@PathVariable long orderId, Authentication authentication) {
        inventoryService.issue(orderId, authentication.getName());
    }
}
