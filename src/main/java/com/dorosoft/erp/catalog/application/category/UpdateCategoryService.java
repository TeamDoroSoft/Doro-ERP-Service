package com.dorosoft.erp.catalog.application.category;

import com.dorosoft.erp.catalog.application.port.CategoryRepository;
import com.dorosoft.erp.catalog.domain.category.Category;
import com.dorosoft.erp.catalog.domain.category.CategoryNotFoundException;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 카테고리명 변경(FR-MENU-003). expectedVersion은 클라이언트의 If-Match 값에 대응한다. */
@Service
public class UpdateCategoryService {

    private final CategoryRepository categoryRepository;

    public UpdateCategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional
    public Category rename(UUID categoryId, String newName, long expectedVersion) {
        Category current = categoryRepository.findById(categoryId).orElseThrow(() -> new CategoryNotFoundException(categoryId));
        if (current.version() != expectedVersion) {
            throw new OptimisticLockingFailureException(
                    "Category가 다른 요청에서 변경되었습니다. categoryId=" + categoryId + ", 요청 version=" + expectedVersion
                            + ", 현재 version=" + current.version());
        }
        return categoryRepository.save(current.rename(newName));
    }
}
