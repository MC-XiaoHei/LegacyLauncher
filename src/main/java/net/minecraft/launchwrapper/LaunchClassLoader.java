package net.minecraft.launchwrapper;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.Attributes;
import java.util.jar.Attributes.Name;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import static java.util.Objects.requireNonNull;


public class LaunchClassLoader extends URLClassLoader {
    private static final Logger logger = LogManager.getLogger("LaunchWrapper");
    public static final int BUFFER_SIZE = 1 << 12;
    private List<URL> sources;
    private ClassLoader parent = getClass().getClassLoader();

    private List<IClassTransformer> transformers = new ArrayList<>(2);
    private Map<String, Class<?>> cachedClasses = new ConcurrentHashMap<>();
    private Set<String> invalidClasses = new HashSet<>(1000);

    private Set<String> classLoaderExceptions = new HashSet<>();
    private Set<String> transformerExceptions = new HashSet<>();
    private Map<String,byte[]> resourceCache = new ConcurrentHashMap<>(1000);
    private Set<String> negativeResourceCache = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

    @Nullable private IClassNameTransformer renameTransformer = null;

    private final ThreadLocal<byte[]> loadBuffer = new ThreadLocal<>();

    private static final String[] RESERVED_NAMES = {"CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9", "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"};

    private static final boolean DEBUG = Boolean.parseBoolean(System.getProperty("legacy.debugClassLoading", "false"));
    private static final boolean DEBUG_FINER = DEBUG && Boolean.parseBoolean(System.getProperty("legacy.debugClassLoadingFiner", "false"));
    private static final boolean DEBUG_SAVE = DEBUG && Boolean.parseBoolean(System.getProperty("legacy.debugClassLoadingSave", "false"));
    private static final Path DUMP_PATH = Paths.get(System.getProperty("legacy.classDumpPath", "./classloader.out"));

    LaunchClassLoader(URL[] sources) {
        super(sources, null);
        this.sources = new ArrayList<>(Arrays.asList(sources));

        // classloader exclusions
        addClassLoaderExclusion("java.");
        addClassLoaderExclusion("sun.");
        addClassLoaderExclusion("org.lwjgl.");
        addClassLoaderExclusion("org.apache.logging.");
        addClassLoaderExclusion("net.minecraft.launchwrapper.");

        // transformer exclusions
        addTransformerExclusion("javax.");
        addTransformerExclusion("argo.");
        addTransformerExclusion("org.objectweb.asm.");
        addTransformerExclusion("com.google.common.");
        addTransformerExclusion("org.bouncycastle.");
        addTransformerExclusion("net.minecraft.launchwrapper.injector.");

        if(DEBUG_SAVE) {
            try {
                if(Files.exists(DUMP_PATH))
                    Files.walk(DUMP_PATH).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
                Files.createDirectories(DUMP_PATH);
                logger.info("DEBUG_SAVE Enabled, saving all classes to \"{}\"", DUMP_PATH.toString());
            } catch (IOException e) {
                logger.warn("Failed to set up DEBUG_SAVE", e);
            }
        }
    }

    /**
     * Registers transformer class
     *
     * @param transformerClassName Fully qualified transformer class name, see {@link Class#getName()}
     */
    public void registerTransformer(@NotNull String transformerClassName) {
        try {
            IClassTransformer transformer = (IClassTransformer) loadClass(transformerClassName).newInstance();
            transformers.add(transformer);
            if (transformer instanceof IClassNameTransformer && renameTransformer == null)
                renameTransformer = (IClassNameTransformer) transformer;
        } catch (Exception e) {
            logger.log(Level.ERROR, "A critical problem occurred registering the ASM transformer class {}", transformerClassName, e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Class<?> findClass(final String name) throws ClassNotFoundException {
        if (invalidClasses.contains(name)) {
            throw new ClassNotFoundException(name);
        }

        for (final String exception : classLoaderExceptions) {
            if(name.startsWith(exception))
                return parent.loadClass(name);
        }

        if (cachedClasses.containsKey(name))
            return cachedClasses.get(name);

        for (final String exception : transformerExceptions) {
            if (name.startsWith(exception)) {
                try {
                    final Class<?> clazz = super.findClass(name);
                    cachedClasses.put(name, clazz);
                    return clazz;
                } catch (ClassNotFoundException e) {
                    invalidClasses.add(name);
                    throw e;
                }
            }
        }

        try {
            final String transformedName = transformName(name);
            if (cachedClasses.containsKey(transformedName)) {
                return cachedClasses.get(transformedName);
            }

            final String untransformedName = untransformName(name);

            final int lastDot = untransformedName.lastIndexOf('.');
            final String packageName = lastDot == -1 ? "" : untransformedName.substring(0, lastDot);
            final String fileName = untransformedName.replace('.', '/').concat(".class");
            URLConnection urlConnection = findCodeSourceConnectionFor(fileName);

            CodeSigner[] signers = null;

            if (lastDot > -1 && !untransformedName.startsWith("net.minecraft.")) {
                if (urlConnection instanceof JarURLConnection) {
                    final JarURLConnection jarURLConnection = (JarURLConnection) urlConnection;
                    final JarFile jarFile = jarURLConnection.getJarFile();

                    if (jarFile != null && jarFile.getManifest() != null) {
                        final Manifest manifest = jarFile.getManifest();
                        final JarEntry entry = jarFile.getJarEntry(fileName);

                        Package pkg = getPackage(packageName);
                        getClassBytes(untransformedName);
                        signers = entry.getCodeSigners();
                        if (pkg == null) {
                            pkg = definePackage(packageName, manifest, jarURLConnection.getJarFileURL());
                        } else {
                            if (pkg.isSealed() && !pkg.isSealed(jarURLConnection.getJarFileURL())) {
                                logger.error("The jar file {} is trying to seal already secured path {}", jarFile.getName(), packageName);
                            } else if (isSealed(packageName, manifest)) {
                                logger.error("The jar file {} has a security seal for path {}, but that path is defined and not secure", jarFile.getName(), packageName);
                            }
                        }
                    }
                } else {
                    Package pkg = getPackage(packageName);
                    if (pkg == null) {
                        pkg = definePackage(packageName, null, null, null, null, null, null, null);
                    } else if (pkg.isSealed()) {
                        URL url = urlConnection != null ? urlConnection.getURL() : null;
                        logger.error("The URL {} is defining elements for sealed path {}", url, packageName);
                    }
                }
            }

            byte[] classData = requireNonNull(getClassBytes(untransformedName));
            final byte[] transformedClass = runTransformers(untransformedName, transformedName, classData);
            if (DEBUG_SAVE) {
                saveTransformedClass(transformedClass, transformedName);
            }

            final CodeSource codeSource = urlConnection == null ? null : new CodeSource(urlConnection.getURL(), signers);
            final Class<?> clazz = defineClass(transformedName, transformedClass, 0, transformedClass.length, codeSource);
            cachedClasses.put(transformedName, clazz);
            return clazz;
        } catch (Throwable e) {
            invalidClasses.add(name);
            if (DEBUG) logger.log(Level.TRACE, "Exception encountered attempting classloading of {}", name, e);
            throw new ClassNotFoundException(name, e);
        }
    }

    private void saveTransformedClass(@NotNull byte[] data, @NotNull String transformedName) throws IOException {
        Path classFile = Paths.get(DUMP_PATH.toString(), transformedName.replace('.', File.separatorChar) + ".class");

        if(Files.notExists(classFile.getParent()))
            Files.createDirectories(classFile);

        if(Files.exists(classFile)) {
            logger.warn("Transformed class \"{}\" already exists! Deleting old class", transformedName);
            Files.delete(classFile);
        }

        try(OutputStream output = Files.newOutputStream(classFile, StandardOpenOption.CREATE_NEW)) {
            logger.debug("Saving transformed class \"{}\" to \"{}\"", transformedName, classFile.toString());
            output.write(data);
        } catch (IOException ex) {
            logger.log(Level.WARN, "Could not save transformed class \"{}\"", transformedName, ex);
        }
    }

    @NotNull
    private String untransformName(@NotNull String name) {
        return renameTransformer != null ? renameTransformer.unmapClassName(name) : name;
    }

    @NotNull
    private String transformName(@NotNull String name) {
        return renameTransformer != null ? renameTransformer.remapClassName(name) : name;
    }

    private boolean isSealed(@NotNull String path, @NotNull Manifest manifest) {
        Attributes attributes = manifest.getAttributes(path);
        String sealed = attributes != null ? attributes.getValue(Name.SEALED) : null;

        if(sealed == null)
            sealed = (attributes = manifest.getMainAttributes()) != null ? attributes.getValue(Name.SEALED) : null;
        return "true".equalsIgnoreCase(sealed);
    }

    @Nullable
    private URLConnection findCodeSourceConnectionFor(@NotNull String name) {
        final URL resource = findResource(name);
        if (resource != null) {
            try {
                return resource.openConnection();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        return null;
    }

    @NotNull
    private byte[] runTransformers(@NotNull String name, @NotNull String transformedName, @NotNull byte[] basicClass) {
        if (DEBUG_FINER) {
            logger.trace("Beginning transform of {{} ({})} Start Length: {}", name, transformedName, basicClass.length);
            for (final IClassTransformer transformer : transformers) {
                final String transName = transformer.getClass().getName();
                logger.trace("Before Transformer {{} ({})} {}: {}", name, transformedName, transName, basicClass.length);
                basicClass = transformer.transform(name, transformedName, basicClass);
                logger.trace("After  Transformer {{} ({})} {}: {}", name, transformedName, transName, basicClass.length);
            }
            logger.trace("Ending transform of {{} ({})} Start Length: {}", name, transformedName, basicClass.length);
        } else {
            for (final IClassTransformer transformer : transformers) {
                basicClass = transformer.transform(name, transformedName, basicClass);
            }
        }
        return basicClass;
    }

    /**
     * Adds an {@link URL} to classloader
     *
     * @param url {@link URL} to add
     */
    @Override
    public void addURL(final URL url) {
        super.addURL(url);
        sources.add(url);
    }

    /**
     * Gets list of added {@link URL}s to this classloader
     *
     * @return Unmodifiable list of added {@link URL}s
     */
    @NotNull
    public List<URL> getSources() {
        return Collections.unmodifiableList(sources);
    }

    @Nullable
    private byte[] readFully(@NotNull InputStream stream) {
        try {
            byte[] buffer = getOrCreateBuffer();

            int read;
            int totalLength = 0;
            while ((read = stream.read(buffer, totalLength, buffer.length - totalLength)) != -1) {
                totalLength += read;

                // Extend our buffer
                if (totalLength >= buffer.length - 1) {
                    byte[] newBuffer = new byte[buffer.length + BUFFER_SIZE];
                    System.arraycopy(buffer, 0, newBuffer, 0, buffer.length);
                    buffer = newBuffer;
                }
            }

            final byte[] result = new byte[totalLength];
            System.arraycopy(buffer, 0, result, 0, totalLength);
            return result;
        } catch (Throwable t) {
            logger.warn("Problem loading class", t);
            return null;
        }
    }

    @NotNull
    private byte[] getOrCreateBuffer() {
        byte[] buffer = loadBuffer.get();
        if (buffer == null) {
            loadBuffer.set(new byte[BUFFER_SIZE]);
            buffer = loadBuffer.get();
        }
        return buffer;
    }

    /**
     * Gets list of registered {@link IClassTransformer} instances
     *
     * @return List of registered {@link IClassTransformer} instances
     */
    @NotNull
    public List<IClassTransformer> getTransformers() {
        return Collections.unmodifiableList(transformers);
    }

    /**
     * Adds classloader exclusion. Fully qualified class names starting with {@code toExclude} will be loaded
     * from parent classloader
     *
     * @param toExclude Part of fully qualified class name
     * @deprecated Use {@link #getClassLoaderExclusions()} instead
     */
    @Deprecated
    public void addClassLoaderExclusion(@NotNull String toExclude) {
        classLoaderExceptions.add(toExclude);
    }

    /**
     * Gets a {@link Set} of classloader exclusions.
     *
     * Classlaoder exclusions look like this: {@code com.mojang.authlib.}, so that means all classes and subclasses
     * in {@code com.mojang.authlib} class would be loaded from parent classloader
     *
     * @return {@link Set} of classloader exclusions
     */
    public Set<String> getClassLoaderExclusions() {
        return classLoaderExceptions;
    }

    /**
     * Adds transformer exclusion. Given classes won't be transformed by {@link IClassTransformer}s
     *
     * @param toExclude Part of fully qualified class name
     * @see #getTransformers() For list of registered transformers
     * @deprecated Use {@link #getTransformerExclusions()} instead.
     */
    @Deprecated
    public void addTransformerExclusion(@NotNull String toExclude) {
        transformerExceptions.add(toExclude);
    }

    /**
     * Gets a {@link Set} of transformer exclusions.
     *
     * Transformer exclusions look like this: {@code com.mojang.authlib.}, so that means all classes and subclasses
     * in {@code com.mojang.authlib} class won't be transformed
     *
     * @return {@link Set} of transformer exclusions.
     */
    public Set<String> getTransformerExclusions() {
        return transformerExceptions;
    }

    /**`
     * Gets class raw bytes
     *
     * @param name Class name
     * @return Class raw bytes, or null if class was not found
     */
    @Nullable
    public byte[] getClassBytes(@NotNull String name) {
        if (negativeResourceCache.contains(name)) {
            return null;
        } else if (resourceCache.containsKey(name)) {
            return resourceCache.get(name);
        }
        if (name.indexOf('.') == -1) {
            for (final String reservedName : RESERVED_NAMES) {
                if (name.toUpperCase(Locale.ENGLISH).startsWith(reservedName)) {
                    final byte[] data = getClassBytes("_" + name);
                    if (data != null) {
                        resourceCache.put(name, data);
                        return data;
                    }
                }
            }
        }

        String resourcePath = name.replace('.', '/').concat(".class");
        URL classResource = findResource(resourcePath);
        if (classResource == null) {
            if (DEBUG) logger.trace("Failed to find class resource {}", resourcePath);
            negativeResourceCache.add(name);
            return null;
        }
        try(InputStream classStream = classResource.openStream()) {
            if (DEBUG) logger.trace("Loading class {} from resource {}", name, classResource.toString());
            byte[] data = requireNonNull(readFully(classStream));
            resourceCache.put(name, data);
            return data;
        } catch (Exception e) {
            if(DEBUG) logger.trace("Failed to load class {} from resource {}", name, classResource.toString());
            negativeResourceCache.add(name);
            return null;
        }
    }

    /**
     * Clears negative resource entries (resources which failed to load in this classloader)
     *
     * @param entriesToClear Entries to clear
     */
    public void clearNegativeEntries(@NotNull Set<String> entriesToClear) {
        negativeResourceCache.removeAll(entriesToClear);
    }
}
