package com.dorosoft.erp.store.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

// 엔티티 타입이 package-private이므로 이 인터페이스도 패키지 밖에서는 사용할 수 없다.
public interface StoreProfileJpaRepository extends JpaRepository<StoreProfileEntity, UUID> {}
