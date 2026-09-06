package fr.nivcoo.utilsz.core.messaging.backend;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedisMessageBackendTest {

    @Test
    void directCapabilityRequiresCanonicalVersionedInstanceId() {
        String uuid = UUID.randomUUID().toString();

        assertTrue(RedisMessageBackend.directCapable("r1:" + uuid));
        assertFalse(RedisMessageBackend.directCapable(uuid));
        assertFalse(RedisMessageBackend.directCapable("r1:not-a-uuid"));
        assertFalse(RedisMessageBackend.directCapable("r2:" + uuid));
    }

    @Test
    void directChannelIsScopedByBaseChannelAndInstance() {
        String instanceId = "r1:" + UUID.randomUUID();

        assertEquals(
                "website:utilsz:direct:" + instanceId,
                RedisMessageBackend.directChannel("website", instanceId)
        );
    }
}
