package com.forgeflow.erp.order;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BomRequirementCalculatorTest {
    @Test
    void multipliesPerUnitQuantityByPlannedQuantityAtFourDecimalPlaces() {
        BigDecimal required = BomRequirementCalculator.totalRequired(
                new BigDecimal("0.1250"), 120);

        assertThat(required).isEqualByComparingTo("15.0000");
        assertThat(required.scale()).isEqualTo(4);
    }

    @Test
    void rejectsNonPositiveBomQuantityOrPlan() {
        assertThatThrownBy(() -> BomRequirementCalculator.totalRequired(
                new BigDecimal("0.0000"), 120)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BomRequirementCalculator.totalRequired(
                new BigDecimal("0.1250"), 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
