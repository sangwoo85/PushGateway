package com.sangwoo.push.api;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.sangwoo.push.service.BusinessDirectory;
import com.sangwoo.push.service.BusinessDirectory.Entry;
import com.sangwoo.push.service.EnrollmentQrService;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

abstract class EnrollmentContract {
    @Autowired MockMvc mvc;
    @MockitoBean BusinessDirectory directory;
    @MockitoBean EnrollmentQrService qr;
    private static final String URL = "/internal/push/enrollment/qr";
    private static final byte[] PNG = { (byte) 137, 80, 78, 71, 13, 10, 26, 10 };

    @BeforeEach
    void setup() {
        when(directory.findUserById("user-1")).thenReturn(Optional.of(new Entry("user-1", "Employee", "dept-1")));
        when(directory.findDepartmentById("dept-1")).thenReturn(Optional.of(new Entry("dept-1", "Team", null)));
        when(qr.issue("user-1", "dept-1")).thenReturn(new EnrollmentQrService.IssuedQr(PNG, Instant.now().plusSeconds(180)));
    }

    private MockHttpServletRequestBuilder request() {
        return get(URL).param("userId", "user-1").param("departmentId", "dept-1");
    }

    @Test void anonymousLoopbackCannotIssue() throws Exception {
        mvc.perform(request()).andExpect(status().isUnauthorized());
        verifyNoInteractions(qr, directory);
    }

    @Test void testerCannotIssue() throws Exception {
        mvc.perform(request().with(user("tester").roles("PUSH_TESTER"))).andExpect(status().isForbidden());
        verifyNoInteractions(qr, directory);
    }

    @Test void ordinaryUserCannotIssueForAnotherUser() throws Exception {
        mvc.perform(request().with(user("user-2").roles("USER"))).andExpect(status().isForbidden());
        verifyNoInteractions(qr, directory);
    }

    @Test void issuerGetsBinaryPngWithoutCaching() throws Exception {
        mvc.perform(request().with(user("issuer").roles("PUSH_ENROLLMENT_ISSUER")))
                .andExpect(status().isOk()).andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(PNG)).andExpect(header().string("Cache-Control", "no-store"));
        verify(qr).issue("user-1", "dept-1");
    }

    @Test void adminCanIssue() throws Exception {
        mvc.perform(request().with(user("admin").roles("PUSH_ADMIN"))).andExpect(status().isOk());
    }

    @Test void missingAndBlankAndOverlongIdsAreRejected() throws Exception {
        mvc.perform(get(URL).with(user("admin").roles("PUSH_ADMIN")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        for (String invalid : new String[] {" ", "x".repeat(129), "user\n1"}) {
            mvc.perform(get(URL).param("userId", invalid).param("departmentId", "dept-1")
                    .with(user("admin").roles("PUSH_ADMIN")))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        verifyNoInteractions(qr, directory);
    }

    @Test void missingUserCannotAllocateTopics() throws Exception {
        when(directory.findUserById("user-1")).thenReturn(Optional.empty());
        mvc.perform(request().with(user("admin").roles("PUSH_ADMIN")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        verifyNoInteractions(qr);
    }

    @Test void missingDepartmentCannotAllocateTopics() throws Exception {
        when(directory.findDepartmentById("dept-1")).thenReturn(Optional.empty());
        mvc.perform(request().with(user("admin").roles("PUSH_ADMIN")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("DEPARTMENT_NOT_FOUND"));
        verifyNoInteractions(qr);
    }

    @Test void wrongDepartmentCannotAllocateTopics() throws Exception {
        when(directory.findUserById("user-1")).thenReturn(Optional.of(new Entry("user-1", "Employee", "another")));
        mvc.perform(request().with(user("admin").roles("PUSH_ADMIN")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DEPARTMENT_MISMATCH"));
        verifyNoInteractions(qr);
    }

    @Test void unconfiguredDirectoryFailsClosed() throws Exception {
        when(directory.findUserById("user-1")).thenThrow(new UnsupportedOperationException("private connection details"));
        mvc.perform(request().with(user("admin").roles("PUSH_ADMIN")))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("DIRECTORY_UNAVAILABLE"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private connection details"))));
        verifyNoInteractions(qr);
    }

    @Test void inexactDirectoryResultIsRejected() throws Exception {
        when(directory.findUserById("user-1")).thenReturn(Optional.of(new Entry("user-2", "Employee", "dept-1")));
        mvc.perform(request().with(user("admin").roles("PUSH_ADMIN")))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("DIRECTORY_UNAVAILABLE"));
        verifyNoInteractions(qr);
    }

    @Test void signingFailureIsSanitized() throws Exception {
        when(qr.issue("user-1", "dept-1")).thenThrow(new IllegalStateException("private key path"));
        mvc.perform(request().with(user("admin").roles("PUSH_ADMIN")))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("QR_ISSUANCE_FAILED"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }
}
