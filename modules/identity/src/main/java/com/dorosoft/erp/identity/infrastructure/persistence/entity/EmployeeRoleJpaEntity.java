package com.dorosoft.erp.identity.infrastructure.persistence.entity;

import com.dorosoft.erp.identity.domain.role.AssignmentSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "employee_role")
public class EmployeeRoleJpaEntity {
    @Id
    @Column(name = "employee_account_id", nullable = false, columnDefinition = "CHAR(36)")
    private String accountId;
    @Column(name = "role_id", nullable = false, columnDefinition = "CHAR(36)")
    private String roleId;
    @Column(name = "assigned_by", columnDefinition = "CHAR(36)")
    private String assignedBy;
    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_source", nullable = false, length = 20)
    private AssignmentSource assignmentSource;
    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    protected EmployeeRoleJpaEntity() {
    }

    public static EmployeeRoleJpaEntity of(
            String accountId, String roleId, String assignedBy,
            AssignmentSource source, Instant assignedAt
    ) {
        EmployeeRoleJpaEntity entity = new EmployeeRoleJpaEntity();
        entity.accountId = accountId;
        entity.roleId = roleId;
        entity.assignedBy = assignedBy;
        entity.assignmentSource = source;
        entity.assignedAt = assignedAt;
        return entity;
    }

    public String accountId() { return accountId; }
    public String roleId() { return roleId; }
    public String assignedBy() { return assignedBy; }
    public AssignmentSource assignmentSource() { return assignmentSource; }
    public Instant assignedAt() { return assignedAt; }
}
