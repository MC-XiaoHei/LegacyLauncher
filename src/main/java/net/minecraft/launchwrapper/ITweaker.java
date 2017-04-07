package net.minecraft.launchwrapper;

import java.io.File;
import java.util.List;

public interface ITweaker {
    void injectIntoClassLoader(LaunchClassLoader classLoader);
    String getLaunchTarget();
    String[] getLaunchArguments();

    default void acceptOptions(List<String> args) {
        acceptOptions(args, null, null, null);
    }

    @Deprecated
    default void acceptOptions(List<String> args, File gameDir, final File assetsDir, String profile) {
        throw new IllegalStateException("Please implement this method.");
    }
}
