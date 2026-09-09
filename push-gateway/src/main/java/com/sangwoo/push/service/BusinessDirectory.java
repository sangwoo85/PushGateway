package com.sangwoo.push.service;

import java.util.List;
import java.util.Optional;

/** 운영 업무 시스템의 사용자·부서 조회 구현체가 교체하는 읽기 전용 포트. */
public interface BusinessDirectory {
    List<Entry> searchUsers(String query, int limit);
    List<Entry> searchDepartments(String query, int limit);

    /** Exact, active-user lookup for production enrollment; never use fuzzy search here. */
    default Optional<Entry> findUserById(String userId) {
        throw new UnsupportedOperationException("Exact business directory lookup is not configured");
    }

    /** Exact, active-department lookup. Empty means missing or inactive. */
    default Optional<Entry> findDepartmentById(String departmentId) {
        throw new UnsupportedOperationException("Exact business directory lookup is not configured");
    }

    record Entry(String id, String displayName, String departmentId) {}
}
