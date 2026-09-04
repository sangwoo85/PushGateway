package com.example.pushgateway.api;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.example.pushgateway.repository.PushDeviceRepository;
import java.security.Principal;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class PushDeviceControllerTest {
    private final PushDeviceRepository repository = mock(PushDeviceRepository.class);
    private final PushDeviceController controller = new PushDeviceController(repository);

    @Test
    void derivesUserFromPrincipal() {
        Principal principal = () -> "authenticated-user";
        controller.register(new PushDeviceController.RegisterDeviceRequest("registration", "instance", "1.0"),
                principal);
        verify(repository).upsertIos("authenticated-user", "registration", "instance", "1.0");
    }

    @Test
    void rejectsUnauthenticatedRegistration() {
        assertThatThrownBy(() -> controller.register(
                new PushDeviceController.RegisterDeviceRequest("registration", "instance", "1.0"), null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401");
    }
}
