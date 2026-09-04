package com.example.pushgateway.service;

import java.util.List;

/** 운영 업무 시스템의 사용자·부서 조회 구현체가 교체하는 읽기 전용 포트. */
public interface BusinessDirectory {
    List<Entry> searchUsers(String query, int limit);
    List<Entry> searchDepartments(String query, int limit);

    record Entry(String id, String displayName, String departmentId) {}
}
