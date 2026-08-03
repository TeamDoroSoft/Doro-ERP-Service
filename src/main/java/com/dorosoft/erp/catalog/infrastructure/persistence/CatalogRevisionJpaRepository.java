package com.dorosoft.erp.catalog.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface CatalogRevisionJpaRepository extends JpaRepository<CatalogRevisionEntity, UUID> {}
