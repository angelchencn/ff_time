package oracle.apps.hcm.formulas.core.jersey.model;

import java.time.LocalDateTime;

/**
 * Formula class — maps to FF_FORMULAS_VL in Fusion DB.
 * Pure JDBC, no JPA annotations.
 */
public final class Formula {
    private final Long id;
    private final String name;
    private final String formulaType;
    private final String code;
    private final String description;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public Formula(Long id, String name, String formulaType, String code,
                   String description, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.name = name;
        this.formulaType = formulaType;
        this.code = code;
        this.description = description;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long id() { return id; }
    public String name() { return name; }
    public String formulaType() { return formulaType; }
    public String code() { return code; }
    public String description() { return description; }
    public LocalDateTime createdAt() { return createdAt; }
    public LocalDateTime updatedAt() { return updatedAt; }
}
