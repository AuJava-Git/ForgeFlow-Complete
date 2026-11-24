package com.forgeflow.purchasing;

import com.forgeflow.purchasing.PurchaseOrderDtos.PurchaseOrderView;
import com.forgeflow.purchasing.PurchaseOrderDtos.PurchaseReceiptRequest;
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
@RequestMapping("/api/purchasing")
public class PurchasingController {
    private final PurchasingService purchasingService;

    public PurchasingController(PurchasingService purchasingService) {
        this.purchasingService = purchasingService;
    }

    @GetMapping("/orders")
    public List<PurchaseOrderView> list() {
        return purchasingService.list();
    }

    @PostMapping("/production-orders/{productionOrderId}/shortage-orders")
    @ResponseStatus(HttpStatus.CREATED)
    public PurchaseOrderView createForShortages(@PathVariable long productionOrderId, Authentication authentication) {
        return purchasingService.createForShortages(productionOrderId, authentication.getName());
    }

    @PostMapping("/orders/{purchaseOrderId}/receipts")
    public PurchaseOrderView receive(@PathVariable long purchaseOrderId,
                                     @Valid @RequestBody PurchaseReceiptRequest request,
                                     Authentication authentication) {
        return purchasingService.receive(purchaseOrderId, request, authentication.getName());
    }
}
