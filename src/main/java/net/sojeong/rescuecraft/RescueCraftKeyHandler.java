package net.sojeong.rescuecraft;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.lwjgl.glfw.GLFW;

public final class RescueCraftKeyHandler {

    private static boolean jWasDown  = false;
    private static boolean kWasDown  = false;
    private static boolean oWasDown  = false;
    private static boolean upWasDown = false;
    private static boolean dnWasDown = false;

    private RescueCraftKeyHandler() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.player == null || mc.screen != null) {
                jWasDown = false;
                kWasDown = false;
                oWasDown = false;
                return;
            }

            long win = mc.getWindow().handle();

            boolean jDown = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_J) == GLFW.GLFW_PRESS;
            if (jDown && !jWasDown) RescueCraftHud.toggleTodo();
            jWasDown = jDown;

            boolean kDown = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_K) == GLFW.GLFW_PRESS;
            if (kDown && !kWasDown) RescueCraftHud.toggleHelp();
            kWasDown = kDown;

            boolean oDown = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_O) == GLFW.GLFW_PRESS;
            if (oDown && !oWasDown) RescueCraftHud.toggleHistory();
            oWasDown = oDown;

            if (RescueCraftHud.isHistoryVisible()) {
                boolean upDown = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_UP) == GLFW.GLFW_PRESS;
                if (upDown && !upWasDown) RescueCraftHud.scrollHistory(1);
                upWasDown = upDown;

                boolean dnDown = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_DOWN) == GLFW.GLFW_PRESS;
                if (dnDown && !dnWasDown) RescueCraftHud.scrollHistory(-1);
                dnWasDown = dnDown;
            } else {
                upWasDown = false;
                dnWasDown = false;
            }
        });
    }
}