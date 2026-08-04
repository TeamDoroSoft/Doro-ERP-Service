package com.dorosoft.erp.catalog.infrastructure.persistence;

import com.dorosoft.erp.catalog.application.port.CatalogRevisionRepository;
import com.dorosoft.erp.catalog.domain.revision.CatalogRevision;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

/** JPA 엔티티와 도메인 CatalogRevision 사이의 변환을 전담한다. 엔티티는 이 패키지 밖으로 나가지 않는다. */
@Repository
public class JpaCatalogRevisionRepositoryAdapter implements CatalogRevisionRepository {

    private final CatalogRevisionJpaRepository jpaRepository;

    public JpaCatalogRevisionRepositoryAdapter(CatalogRevisionJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<CatalogRevision> findCurrent() {
        List<CatalogRevisionEntity> entities = jpaRepository.findAll();
        if (entities.isEmpty()) {
            return Optional.empty();
        }
        if (entities.size() > 1) {
            throw new IllegalStateException(
                    "업체 Schema에는 catalog_revision이 하나만 존재해야 합니다. 실제 행 수: " + entities.size());
        }
        return Optional.of(toDomain(entities.getFirst()));
    }

    @Override
    public boolean exists() {
        return jpaRepository.count() > 0;
    }

    @Override
    public CatalogRevision save(CatalogRevision revision) {
        CatalogRevisionEntity entity = jpaRepository.findById(revision.catalogId()).orElse(null);

        if (entity == null) {
            entity = new CatalogRevisionEntity(revision.catalogId());
        } else {
            if (entity.getRevision() != revision.revision()) {
                throw new OptimisticLockingFailureException(
                        "Catalog Revision이 다른 Transaction에서 변경되었습니다. catalogId="
                                + revision.catalogId()
                                + ", 요청 revision="
                                + revision.revision()
                                + ", 현재 revision="
                                + entity.getRevision());
            }
            entity.touch();
        }

        CatalogRevisionEntity saved = jpaRepository.saveAndFlush(entity);
        return toDomain(saved);
    }

    @Override
    public CatalogRevision advance() {
        int updated = jpaRepository.bumpRevision(Instant.now());
        if (updated != 1) {
            throw new IllegalStateException(
                    "업체 Schema에는 catalog_revision이 하나만 존재해야 합니다. 실제 갱신 행 수: " + updated);
        }
        return findCurrent().orElseThrow(() -> new IllegalStateException("Catalog가 초기화되지 않았습니다"));
    }

    private static CatalogRevision toDomain(CatalogRevisionEntity entity) {
        return new CatalogRevision(entity.getCatalogId(), entity.getRevision(), entity.getUpdatedAt());
    }
}
