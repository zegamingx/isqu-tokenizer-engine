package io.github.zegamingx.compatibility;

public final class JavaConsumer {
    private JavaConsumer() {
    }

    public static String smoke() {
        return "Java " + Runtime.version().feature();
    }
}
