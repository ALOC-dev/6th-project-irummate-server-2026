package com.irummate.domain.notice.repository;

import com.irummate.domain.notice.entity.DormitoryNotice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoticeRepository extends JpaRepository<DormitoryNotice, Long> {
    Page<DormitoryNotice> findByTitleContainingIgnoreCase(String keyword, Pageable pageable);
}
