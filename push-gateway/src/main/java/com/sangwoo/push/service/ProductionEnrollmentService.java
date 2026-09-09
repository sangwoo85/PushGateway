package com.sangwoo.push.service;

import org.springframework.stereotype.Service;

/** Production enrollment validates directory membership before creating any topics. */
@Service
public class ProductionEnrollmentService {
    private final BusinessDirectory directory;
    private final EnrollmentQrService qr;

    public ProductionEnrollmentService(BusinessDirectory directory, EnrollmentQrService qr) {
        this.directory = directory;
        this.qr = qr;
    }

    public EnrollmentQrService.IssuedQr issue(String userId, String departmentId) {
        String user = normalize(userId);
        String department = normalize(departmentId);
        try {
            var employee = directory.findUserById(user)
                    .orElseThrow(() -> new EnrollmentFailure("USER_NOT_FOUND"));
            if (!user.equals(employee.id())) throw new EnrollmentFailure("DIRECTORY_UNAVAILABLE");
            var team = directory.findDepartmentById(department)
                    .orElseThrow(() -> new EnrollmentFailure("DEPARTMENT_NOT_FOUND"));
            if (!department.equals(team.id())) throw new EnrollmentFailure("DIRECTORY_UNAVAILABLE");
            if (!department.equals(employee.departmentId())) {
                throw new EnrollmentFailure("DEPARTMENT_MISMATCH");
            }
        } catch (EnrollmentFailure ex) {
            throw ex;
        } catch (RuntimeException ex) {
            // Do not expose SQL errors, directory internals or employee data in the response.
            throw new EnrollmentFailure("DIRECTORY_UNAVAILABLE");
        }
        try {
            return qr.issue(user, department);
        } catch (RuntimeException ex) {
            throw new EnrollmentFailure("QR_ISSUANCE_FAILED");
        }
    }

    private String normalize(String value) {
        if (value == null || value.isBlank() || value.length() > 128
                || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new EnrollmentFailure("INVALID_REQUEST");
        }
        return value.strip();
    }

    public static final class EnrollmentFailure extends RuntimeException {
        public EnrollmentFailure(String code) { super(code); }
    }
}
