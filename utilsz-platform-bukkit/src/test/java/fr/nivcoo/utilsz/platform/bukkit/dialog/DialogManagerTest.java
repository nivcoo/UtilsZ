package fr.nivcoo.utilsz.platform.bukkit.dialog;

import io.papermc.paper.registry.data.dialog.DialogRegistryEntry;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DialogManagerTest {

    @Test
    void failedDialogBuildDoesNotLeaveATrackedView() {
        UUID playerId = UUID.randomUUID();
        Player player = player(playerId);
        DialogProvider failing = new DialogProvider() {
            @Override
            public Component title(DialogView view) {
                return Component.text("Failure");
            }

            @Override
            public void configureBuilder(
                    DialogView view,
                    DialogRegistryEntry.Builder builder
            ) {
                throw new IllegalStateException("build failed");
            }
        };
        DialogManager manager = new DialogManager();

        assertThrows(Throwable.class,
                () -> manager.open(failing, player));

        assertNull(manager.get(playerId));
    }

    private static Player player(UUID playerId) {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getUniqueId" -> playerId;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    case "toString" -> "Player[" + playerId + ']';
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        return 0;
    }
}
