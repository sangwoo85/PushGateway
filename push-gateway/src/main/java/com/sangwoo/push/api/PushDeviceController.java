package com.sangwoo.push.api;

import com.sangwoo.push.repository.PushDeviceRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/push/devices")
@Deprecated(forRemoval = true)
public class PushDeviceController {
    private final PushDeviceRepository devices;

    public PushDeviceController(PushDeviceRepository devices) {
        this.devices = devices;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void register(@Valid @RequestBody RegisterDeviceRequest request, Principal principal) {
        String userId = authenticatedUser(principal);
        devices.upsertIos(userId, request.registrationId(), request.appInstanceId(), request.appVersion());
    }

    @DeleteMapping("/{appInstanceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disable(@PathVariable @NotBlank @Size(max = 128) String appInstanceId, Principal principal) {
        devices.disableForUser(authenticatedUser(principal), appInstanceId);
    }

    private String authenticatedUser(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
        return principal.getName();
    }

    public record RegisterDeviceRequest(
            @NotBlank @Size(max = 4096) String registrationId,
            @NotBlank @Size(max = 128) String appInstanceId,
            @Size(max = 32) String appVersion) {}
}
