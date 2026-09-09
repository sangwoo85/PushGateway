package com.sangwoo.push.api;

import com.sangwoo.push.service.ProductionEnrollmentService;
import com.sangwoo.push.service.ProductionEnrollmentService.EnrollmentFailure;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/push/enrollment")
public class EnrollmentController {
    private final ProductionEnrollmentService enrollment;

    public EnrollmentController(ProductionEnrollmentService enrollment) {
        this.enrollment = enrollment;
    }

    @GetMapping("/qr")
    public ResponseEntity<byte[]> qr(@RequestParam String userId, @RequestParam String departmentId) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore())
                .body(enrollment.issue(userId, departmentId).png());
    }

    @ExceptionHandler(EnrollmentFailure.class)
    ResponseEntity<ProblemDetail> failure(EnrollmentFailure ex) {
        HttpStatus status = switch (ex.getMessage()) {
            case "INVALID_REQUEST" -> HttpStatus.BAD_REQUEST;
            case "USER_NOT_FOUND", "DEPARTMENT_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "DEPARTMENT_MISMATCH" -> HttpStatus.CONFLICT;
            default -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        return problem(status, ex.getMessage());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ProblemDetail> missingParameter() {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String code) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, switch (code) {
            case "INVALID_REQUEST" -> "사용자 ID와 부서 ID는 각각 1~128자의 유효한 값이어야 합니다.";
            case "USER_NOT_FOUND" -> "활성 사용자를 찾을 수 없습니다.";
            case "DEPARTMENT_NOT_FOUND" -> "활성 부서를 찾을 수 없습니다.";
            case "DEPARTMENT_MISMATCH" -> "사용자의 실제 소속 부서와 일치하지 않습니다.";
            case "DIRECTORY_UNAVAILABLE" -> "업무 사용자·부서 조회 연동을 사용할 수 없습니다.";
            default -> "QR 발급 설정 또는 저장소 상태를 확인해야 합니다.";
        });
        detail.setProperty("code", code);
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON).body(detail);
    }
}
