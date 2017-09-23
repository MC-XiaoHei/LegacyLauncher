package net.minecraft.launchwrapper;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

/**
 * A Tweaker
 */
public interface ITweaker {
    /**
     * Parses application's parameters (which you'll supply to app on command line)
     *
     * @param args {@link List} of application's parameters
     */
    @SuppressWarnings("deprecation")
    default void acceptOptions(@NotNull List<String> args) {
        acceptOptions(args, null, null, null);
    }

    /**
     * Old {@code acceptOptions} method retained for compatibility reasons.
     *
     * @param args Application's arguments
     * @param gameDir unknown
     * @param assetsDir unknown
     * @param profile unknown
     * @deprecated This method is not used internally. See {@link ITweaker#acceptOptions(List)}
     */
    @Deprecated
    default void acceptOptions(@NotNull List<String> args, File gameDir, final File assetsDir, String profile) {
        throw new IllegalStateException("Please implement this method.");
    }

    /**
     * Asks tweaker for transformers and other options
     *
     * @param classLoader Current {@link LaunchClassLoader} instance
     */
    void injectIntoClassLoader(@NotNull LaunchClassLoader classLoader);

    /**
     * Gets arguments which will be added to endpoint's {@code main(String[])}
     *
     * @return String array of arguments
     */
    @NotNull
    String[] getLaunchArguments();

    /**
     * Gets main endpoint class to pass arguments to and start the wrapped application. Only used
     * for main tweaker.
     *
     * @return Endpoint class name
     */
    @NotNull
    String getLaunchTarget();
}
