package de.gematik.refpopp.popp_client.configuration.helper;

import de.gematik.refpopp.popp_client.connector.soap.ServiceEndpointProvider;
import de.gematik.refpopp.popp_client.connector.soap.ServiceEndpoint;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;



class SoapActionVersionHelperTest {

    @Test
    void shouldReturnMajorMinor_whenVersionIsValid() {
        ServiceEndpointProvider provider = mock(ServiceEndpointProvider.class);
        String version = "1.2.3";
        String result = invokeGetVersionFromServiceEndpoint(provider, version);
        assertThat(result).isEqualTo("1.2");
    }

    @Test
    void shouldReturnMajorMinor_whenVersionIsMajorMinorOnly() {
        ServiceEndpointProvider provider = mock(ServiceEndpointProvider.class);
        String version = "2.5";
        String result = invokeGetVersionFromServiceEndpoint(provider, version);
        assertThat(result).isEqualTo("2.5");
    }

    @Test
    void shouldThrowIllegalStateException_whenVersionIsNull() {
        ServiceEndpointProvider provider = mock(ServiceEndpointProvider.class);
        ServiceEndpoint endpoint = mock(ServiceEndpoint.class);
        when(provider.getCardServiceEndpoint()).thenReturn(endpoint);
        when(endpoint.getEndpoint()).thenReturn("endpointUrl");

        assertThatThrownBy(() -> invokeGetVersionFromServiceEndpoint(provider, null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Version is missing for endpointUrl");
    }

    @Test
    void shouldThrowIllegalStateException_whenVersionIsBlank() {
        ServiceEndpointProvider provider = mock(ServiceEndpointProvider.class);
        ServiceEndpoint endpoint = mock(ServiceEndpoint.class);
        when(provider.getCardServiceEndpoint()).thenReturn(endpoint);
        when(endpoint.getEndpoint()).thenReturn("endpointUrl");

        assertThatThrownBy(() -> invokeGetVersionFromServiceEndpoint(provider, "   "))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Version is missing for endpointUrl");
    }

    @Test
    void shouldThrowIllegalArgumentException_whenVersionHasNoDot() {
        ServiceEndpointProvider provider = mock(ServiceEndpointProvider.class);
        ServiceEndpoint endpoint = mock(ServiceEndpoint.class);
        when(provider.getCardServiceEndpoint()).thenReturn(endpoint);
        when(endpoint.getEndpoint()).thenReturn("endpointUrl");

        assertThatThrownBy(() -> invokeGetVersionFromServiceEndpoint(provider, "1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unsupported version format");
    }

    @Test
    void shouldThrowIllegalArgumentException_whenVersionIsDotOnly() {
        ServiceEndpointProvider provider = mock(ServiceEndpointProvider.class);
        ServiceEndpoint endpoint = mock(ServiceEndpoint.class);
        when(provider.getCardServiceEndpoint()).thenReturn(endpoint);
        when(endpoint.getEndpoint()).thenReturn("endpointUrl");

        assertThatThrownBy(() -> invokeGetVersionFromServiceEndpoint(provider, "."))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unsupported version format");
    }

    // Helper to invoke the private static method via reflection
    private String invokeGetVersionFromServiceEndpoint(ServiceEndpointProvider provider, String version) {
        try {
            var method = SoapActionVersionHelper.class.getDeclaredMethod(
                "getVersionFromServiceEndpoint", ServiceEndpointProvider.class, String.class);
            method.setAccessible(true);
            return (String) method.invoke(null, provider, version);
        } catch (Exception e) {
            if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            throw new RuntimeException(e);
        }
    }
}