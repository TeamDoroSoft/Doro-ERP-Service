package com.dorosoft.erp.table.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TableIdempotencyRecordJpaRepository
        extends JpaRepository<TableIdempotencyRecordEntity, String> {}
