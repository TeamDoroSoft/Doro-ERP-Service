package com.dorosoft.erp.catalog.application.port;

import com.dorosoft.erp.catalog.domain.revision.CatalogRevision;
import java.util.Optional;

// 업체 ID·Schema명을 파라미터로 받지 않는다. 배포당 DataSource 1개가 이미 해당 업체 Schema를 가리킨다.
public interface CatalogRevisionRepository {

    Optional<CatalogRevision> findCurrent();

    /**
     * 전달한 revision과 저장된 값이 다르면 낙관적 잠금 충돌로 거부한다.
     * 일치하면 값 변경 유무와 무관하게 Row를 Touch해 revision을 1 증가시킨다.
     * 카테고리·상품 전체 정렬처럼 Catalog Revision을 동시성 기준으로 쓰는 Use Case가 같은 Transaction에서 호출한다.
     */
    CatalogRevision save(CatalogRevision revision);

    /**
     * 낙관적 잠금 비교 없이 Catalog Revision을 원자적으로 1 증가시킨다. 정렬처럼 Catalog Revision
     * 자체를 동시성 기준으로 쓰는 Use Case가 아니라, Category·Product의 다른 모든 변경이 공개 메뉴
     * ETag(Catalog Revision) 갱신을 위해 부수 효과로 호출한다. 여러 변경이 동시에 이 값을 올려도
     * 서로를 거짓 충돌로 실패시키지 않도록 DB 레벨 증가 연산을 쓴다.
     */
    CatalogRevision advance();

    boolean exists();
}
