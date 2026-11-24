package com.forgeflow.quality;

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
@RequestMapping("/api/quality")
public class QualityController {
    private final QualityService qualityService;

    public QualityController(QualityService qualityService) {
        this.qualityService = qualityService;
    }

    @GetMapping("/pending")
    public List<QualityPendingView> pending() {
        return qualityService.pending();
    }

    @GetMapping("/inspections")
    public List<QualityInspectionView> recent() {
        return qualityService.recent();
    }

    @PostMapping("/production-reports/{reportId}/inspect")
    @ResponseStatus(HttpStatus.CREATED)
    public QualityInspectionView inspect(@PathVariable long reportId,
                                         @Valid @RequestBody QualityInspectionRequest request,
                                         Authentication authentication) {
        return qualityService.inspect(reportId, request, authentication.getName());
    }

    @PostMapping("/inspections/{inspectionId}/finished-goods-receipt")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void receiveToFinishedGoods(@PathVariable long inspectionId, Authentication authentication) {
        qualityService.receiveToFinishedGoods(inspectionId, authentication.getName());
    }
}
