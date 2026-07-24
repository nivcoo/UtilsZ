package fr.nivcoo.utilsz.core.config;

import fr.nivcoo.utilsz.core.config.annotations.*;
import fr.nivcoo.utilsz.core.config.annotations.Optional;
import fr.nivcoo.utilsz.core.config.reload.ConfigReloadListener;
import fr.nivcoo.utilsz.core.config.reload.ConfigReloadOptions;
import fr.nivcoo.utilsz.core.config.reload.ConfigReloadTicker;
import fr.nivcoo.utilsz.core.conversion.Converter;
import fr.nivcoo.utilsz.core.conversion.ConverterRegistry;
import fr.nivcoo.utilsz.core.config.text.TextMode;
import fr.nivcoo.utilsz.core.config.validation.Validatable;
import fr.nivcoo.utilsz.core.scheduler.PluginScheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ConfigManager {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_AMP = LegacyComponentSerializer.builder()
            .character('&').hexColors().useUnusualXRepeatedCharacterHexFormat().build();
    private static final LegacyComponentSerializer LEGACY_SEC = LegacyComponentSerializer.builder()
            .character('§').hexColors().useUnusualXRepeatedCharacterHexFormat().build();
    private static final Pattern HEX_AMP = Pattern.compile("&#([A-Fa-f0-9]{6,8})");
    private static final Pattern MINI_HEX = Pattern.compile("<#[0-9a-fA-F]{6,8}>");
    private static final Pattern MINI_TAG = Pattern.compile(
            "</?(?i:(b|i|u|st|obf|r|" +
                    "bold|italic|underlined|strikethrough|obfuscated|reset|" +
                    "color|gradient|rainbow|click|hover|insertion|keybind|font|url|newline|" +
                    "black|dark_blue|dark_green|dark_aqua|dark_red|dark_purple|gold|gray|grey|" +
                    "dark_gray|dark_grey|blue|green|aqua|red|light_purple|yellow|white))" +
                    "(?:[:\\s>]|$)"
    );

    private final File dataFolder;

    public ConfigManager(File dataFolder) {
        this.dataFolder = Objects.requireNonNull(dataFolder, "dataFolder");
    }

    @SuppressWarnings("unchecked")
    private static Converter<Object> findConverter(Field f) {
        WithConverter ann = f.getAnnotation(WithConverter.class);
        if (ann == null) ann = f.getType().getAnnotation(WithConverter.class);
        if (ann != null) {
            try {
                return (Converter<Object>) ann.value().getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        Supplier<Converter<?>> sup = ConverterRegistry.all().get(f.getType());
        return sup != null ? (Converter<Object>) sup.get() : null;
    }

    @SuppressWarnings("unchecked")
    private static Converter<Object> findConverterForClass(Class<?> cls) {
        if (cls == null) return null;

        try {
            WithConverter ann = cls.getAnnotation(WithConverter.class);
            if (ann != null) {
                return (Converter<Object>) ann.value().getDeclaredConstructor().newInstance();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        Supplier<Converter<?>> sup = ConverterRegistry.all().get(cls);
        return sup != null ? (Converter<Object>) sup.get() : null;
    }

    public <T> T load(String relativePath, Class<T> cfgClass) {
        return load(relativePath, cfgClass, newInstance(cfgClass), false);
    }

    @SuppressWarnings("unused")
    public <T> T load(String relativePath, Class<T> cfgClass, Supplier<? extends T> defaults) {
        Objects.requireNonNull(cfgClass, "cfgClass");
        Objects.requireNonNull(defaults, "defaults");
        T instance = Objects.requireNonNull(defaults.get(), "defaults returned null");
        if (!cfgClass.isInstance(instance)) {
            throw new IllegalArgumentException("Defaults must be an instance of " + cfgClass.getName());
        }
        return load(relativePath, cfgClass, instance, true);
    }

    public <T> ConfigReloadTicker<T> watch(String relativePath, Class<T> cfgClass, T current,
                                            PluginScheduler scheduler, ConfigReloadListener<T> listener,
                                            Consumer<Throwable> errorHandler) {
        return watch(relativePath, cfgClass, current, scheduler, listener, errorHandler,
                ConfigReloadOptions.DEFAULT);
    }

    public <T> ConfigReloadTicker<T> watch(String relativePath, Class<T> cfgClass, T current,
                                            PluginScheduler scheduler, ConfigReloadListener<T> listener,
                                            Consumer<Throwable> errorHandler, ConfigReloadOptions options) {
        Objects.requireNonNull(cfgClass, "cfgClass");
        File file = resolveFile(relativePath);
        return new ConfigReloadTicker<>(scheduler, file.toPath(), current,
                () -> load(relativePath, cfgClass), listener, errorHandler, options);
    }

    private <T> T load(String relativePath, Class<T> cfgClass, T instance, boolean mergeDefaults) {
        Objects.requireNonNull(cfgClass, "cfgClass");
        File file = resolveFile(relativePath);
        boolean existed = file.exists();
        Map<String, Object> existing = loadYaml(file);

        Map<String, Object> input = existing;
        if (mergeDefaults) {
            input = new LinkedHashMap<>();
            export(instance, rootName(cfgClass), Map.of(), input, new LinkedHashMap<>());
            mergeYaml(input, existing);
            instance = copyConfigInstance(instance, cfgClass);
        }

        inject(input, instance, rootName(cfgClass));
        validate(instance, rootName(cfgClass));

        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, List<String>> comments = new LinkedHashMap<>();
        export(instance, rootName(cfgClass), existing, out, comments);

        if (shouldWrite(cfgClass, existed)) {
            Comment rootC = cfgClass.getAnnotation(Comment.class);
            String header = (rootC != null) ? String.join("\n", rootC.value()) : null;
            saveText(file, writeYamlWithComments(out, comments, header));
        }
        return instance;
    }

    private static boolean shouldWrite(Class<?> cfgClass, boolean existed) {
        boolean structure = cfgClass.isAnnotationPresent(ConfigStructure.class);
        boolean defaults = cfgClass.isAnnotationPresent(DefaultConfig.class);
        if (structure && defaults) {
            throw new IllegalArgumentException(cfgClass.getName()
                    + " cannot declare both @ConfigStructure and @DefaultConfig");
        }
        if (structure) return false;
        if (defaults) return !existed;
        return true;
    }

    public <T> List<T> loadAll(String relativeDir, Class<T> cfgClass) {
        return loadAll(relativeDir, cfgClass, f -> {
            String n = f.getName().toLowerCase(Locale.ROOT);
            return n.endsWith(".yml") || n.endsWith(".yaml");
        });
    }

    public <T> List<T> loadAll(String relativeDir, Class<T> cfgClass, Predicate<File> filter) {
        File dir = resolveFile(relativeDir);
        if (!dir.exists() && !dir.mkdirs())
            throw new IllegalStateException("Unable to create directory: " + dir);
        File[] files = dir.listFiles(f -> f.isFile() && filter.test(f));
        if (files == null || files.length == 0) return List.of();

        List<T> list = new ArrayList<>(files.length);
        for (File f : files) list.add(load(pathRelativeToData(f), cfgClass));
        return list;
    }

    @SuppressWarnings("unused")
    public <T> List<NamedConfig<T>> loadAllNamed(String relativeDir, Class<T> cfgClass, boolean recursive) {
        return loadAllNamed(relativeDir, cfgClass, recursive, Map.of());
    }

    @SuppressWarnings("unused")
    public <T> List<NamedConfig<T>> loadAllNamed(
            String relativeDir,
            Class<T> cfgClass,
            boolean recursive,
            Map<String, ? extends Supplier<? extends T>> defaultsById
    ) {
        Objects.requireNonNull(cfgClass, "cfgClass");
        Objects.requireNonNull(defaultsById, "defaultsById");
        File dir = resolveFile(relativeDir);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("Unable to create directory: " + dir);
        }

        java.nio.file.Path root = dir.toPath().toAbsolutePath().normalize();
        List<NamedFile> files;
        try (var paths = recursive ? Files.walk(dir.toPath()) : Files.list(dir.toPath())) {
            files = paths
                    .filter(Files::isRegularFile)
                    .filter(ConfigManager::isYamlFile)
                    .map(path -> namedFile(root, path.toFile()))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list config directory: " + dir, e);
        }

        Map<String, NamedFile> byId = new LinkedHashMap<>();
        for (NamedFile file : files) {
            if (byId.putIfAbsent(file.normalizedId(), file) != null) {
                throw new IllegalArgumentException("Duplicate config id '" + file.id() + "' in " + dir);
            }
        }

        Map<String, Supplier<? extends T>> defaults = new LinkedHashMap<>();
        for (Map.Entry<String, ? extends Supplier<? extends T>> entry : defaultsById.entrySet()) {
            String id = normalizeNamedId(entry.getKey());
            String normalizedId = id.toLowerCase(Locale.ROOT);
            Supplier<? extends T> supplier = Objects.requireNonNull(entry.getValue(), "defaults supplier");
            if (defaults.putIfAbsent(normalizedId, supplier) != null) {
                throw new IllegalArgumentException("Duplicate default config id '" + id + "'");
            }
            byId.putIfAbsent(normalizedId, new NamedFile(
                    id,
                    normalizedId,
                    pathRelativeToData(resolveFile(relativeDir + '/' + id + ".yml"))
            ));
        }

        List<NamedFile> ordered = byId.values().stream()
                .sorted(Comparator.comparing(NamedFile::relativePath, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(NamedFile::relativePath))
                .toList();
        List<NamedConfig<T>> configs = new ArrayList<>(ordered.size());
        for (NamedFile file : ordered) {
            Supplier<? extends T> supplier = defaults.get(file.normalizedId());
            T value = supplier == null
                    ? load(file.relativePath(), cfgClass)
                    : load(file.relativePath(), cfgClass, supplier);
            configs.add(new NamedConfig<>(file.id(), file.relativePath(), value));
        }
        return List.copyOf(configs);
    }

    private NamedFile namedFile(java.nio.file.Path root, File file) {
        String localPath = root.relativize(file.toPath().toAbsolutePath().normalize())
                .toString().replace('\\', '/');
        String id = stripYamlExtension(localPath);
        return new NamedFile(id, id.toLowerCase(Locale.ROOT), pathRelativeToData(file));
    }

    private static String normalizeNamedId(String value) {
        if (value == null) throw new IllegalArgumentException("Config id cannot be null");
        String id = value.trim().replace('\\', '/');
        if (id.isBlank() || id.startsWith("/") || id.endsWith("/") || id.contains("//")) {
            throw new IllegalArgumentException("Invalid config id: " + value);
        }
        for (String segment : id.split("/")) {
            if (segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("Invalid config id: " + value);
            }
        }
        String lower = id.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".yml") || lower.endsWith(".yaml")) {
            throw new IllegalArgumentException("Config id must not include an extension: " + value);
        }
        return id;
    }

    private static boolean isYamlFile(java.nio.file.Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".yml") || name.endsWith(".yaml");
    }

    private static String stripYamlExtension(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".yaml")) return path.substring(0, path.length() - 5);
        if (lower.endsWith(".yml")) return path.substring(0, path.length() - 4);
        return path;
    }

    private record NamedFile(String id, String normalizedId, String relativePath) {
    }

    private File resolveFile(String relativePath) {
        if (relativePath == null) throw new IllegalArgumentException("relativePath cannot be null");
        java.nio.file.Path root = dataFolder.toPath().toAbsolutePath().normalize();
        java.nio.file.Path resolved = root.resolve(relativePath).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Config path escapes the data folder: " + relativePath);
        }
        return resolved.toFile();
    }

    private String pathRelativeToData(File f) {
        java.nio.file.Path root = dataFolder.toPath().toAbsolutePath().normalize();
        java.nio.file.Path path = f.toPath().toAbsolutePath().normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Config file escapes the data folder: " + f);
        }
        return root.relativize(path).toString().replace('\\', '/');
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadYaml(File f) {
        if (!f.exists()) return new LinkedHashMap<>();
        try (var r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
            Object o = new Yaml().load(r);
            if (o instanceof Map<?, ?> m) return new LinkedHashMap<>((Map<String, Object>) m);
            if (o == null) return new LinkedHashMap<>();
            throw new IllegalArgumentException("Config root must be a map: " + f);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void saveText(File f, String text) {
        File parent = f.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs())
            throw new IllegalStateException("Unable to create config directory: " + parent);

        try (var w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
            w.write(text);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to save config file: " + f, e);
        }
    }

    private void inject(Map<String, Object> src, Object bean, String prefix) {
        for (Field f : orderedConfigFields(bean.getClass(), false)) {
            if (isStatic(f)) continue;
            f.setAccessible(true);
            String path = keyPath(f, prefix);
            try {
                if (isSectionField(f)) {
                    Object node = getByPath(src, path);
                    if (node instanceof Map<?, ?> m && !m.isEmpty()) {
                        Object sec = f.get(bean);
                        if (sec == null) sec = newInstance(f.getType());
                        f.set(bean, sec);
                        inject(src, sec, path);
                    }
                    continue;
                }

                Object raw = getByPath(src, path);
                Object val = convertFromYaml(f, raw, f.get(bean));
                if (val != null) f.set(bean, val);

            } catch (Exception e) {
                throw new RuntimeException("Inject failed for " + path + ": " + e.getMessage(), e);
            }
        }
    }

    private void export(Object bean, String prefix, Map<String, Object> existing, Map<String, Object> out, Map<String, List<String>> comments) {
        export(bean, prefix, existing, out, comments, false);
    }

    private void export(Object bean, String prefix, Map<String, Object> existing, Map<String, Object> out, Map<String, List<String>> comments, boolean optionalContext) {
        for (Field f : orderedConfigFields(bean.getClass(), true)) {
            if (isStatic(f)) continue;
            String path = keyPath(f, prefix);
            try {
                boolean required = f.isAnnotationPresent(Required.class);
                boolean optional = !required && (optionalContext || isOptionalField(f));
                if (isSectionField(f)) {
                    Object sec = f.get(bean);
                    if (sec != null) {
                        if (optional) {
                            Map<String, Object> tempOut = new LinkedHashMap<>();
                            Map<String, List<String>> tempComments = new LinkedHashMap<>();
                            exportSection(sec, path, existing, tempOut, tempComments, f, true);
                            Object exported = getByPath(tempOut, path);
                            Object existingRaw = getByPath(existing, path);
                            if (existingRaw == null && isEmptyYamlNode(exported)) continue;
                            mergeYaml(out, tempOut);
                            comments.putAll(tempComments);
                            continue;
                        }
                        exportSection(sec, path, existing, out, comments, f, false);
                    }
                } else {
                    Object v = f.get(bean);
                    Object existingRaw = getByPath(existing, path);
                    Object yamlVal = convertToYamlPreserving(f, v, existingRaw);
                    if (optional && (yamlVal == null || existingRaw == null
                            && (isEmptyYamlNode(yamlVal) || isDefaultFieldValue(f, v)))) {
                        continue;
                    }
                    putByPath(out, path, yamlVal);
                    Comment c = f.getAnnotation(Comment.class);
                    if (c != null) comments.put(path, Arrays.asList(c.value()));
                }
            } catch (Exception e) {
                throw new RuntimeException("Export failed for " + path + ": " + e.getMessage(), e);
            }
        }
    }

    private void validate(Object bean, String prefix) {
        Set<Object> traversed = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Object> validated = Collections.newSetFromMap(new IdentityHashMap<>());
        validateBean(bean, prefix, traversed, validated);
    }

    private void validateBean(Object bean, String prefix, Set<Object> traversed, Set<Object> validated) {
        if (!traversed.add(bean)) return;

        for (Field f : orderedConfigFields(bean.getClass(), true)) {
            if (isStatic(f)) continue;
            String path = keyPath(f, prefix);
            try {
                Object v = f.get(bean);

                if (isSectionField(f) && v != null) {
                    validateBean(v, path, traversed, validated);
                    continue;
                }

                if (f.isAnnotationPresent(NotNull.class) && v == null)
                    throw new IllegalArgumentException("Field " + path + " is @NotNull but null");

                Required req = f.getAnnotation(Required.class);
                if (req != null) {
                    switch (v) {
                        case null -> throw new IllegalArgumentException("Field " + path + " is @Required but null");
                        case CharSequence cs when cs.isEmpty() ->
                                throw new IllegalArgumentException("Field " + path + " is @Required but empty");
                        case Collection<?> col when col.isEmpty() ->
                                throw new IllegalArgumentException("Field " + path + " is @Required but empty");
                        default -> {
                        }
                    }
                }

                Range r = f.getAnnotation(Range.class);
                if (r != null && v instanceof Number n) {
                    double d = n.doubleValue();
                    if (d < r.min() || d > r.max())
                        throw new IllegalArgumentException("Field " + path + " out of range [" + r.min() + "," + r.max() + "]: " + d);
                }

                validateValue(v, path, traversed, validated);

            } catch (RuntimeException re) {
                throw re;
            } catch (Exception e) {
                throw new RuntimeException("Validate failed for " + path + ": " + e.getMessage(), e);
            }
        }

        validateOnce(bean, validated);
    }

    private void validateValue(Object value, String path, Set<Object> traversed, Set<Object> validated) {
        if (value == null) return;

        if (value instanceof Map<?, ?> map) {
            if (!traversed.add(value)) return;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                validateValue(entry.getValue(), path + "[" + mapKeyToYaml(entry.getKey()) + "]", traversed, validated);
            }
            validateOnce(value, validated);
            return;
        }

        if (value instanceof Iterable<?> iterable) {
            if (!traversed.add(value)) return;
            int index = 0;
            for (Object element : iterable) {
                validateValue(element, path + "[" + index++ + "]", traversed, validated);
            }
            validateOnce(value, validated);
            return;
        }

        if (value.getClass().isArray()) {
            if (!traversed.add(value)) return;
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                validateValue(Array.get(value, i), path + "[" + i + "]", traversed, validated);
            }
            validateOnce(value, validated);
            return;
        }

        if (isConfigPojo(value.getClass())) {
            validateBean(value, path, traversed, validated);
            return;
        }

        validateOnce(value, validated);
    }

    private static void validateOnce(Object value, Set<Object> validated) {
        if (value instanceof Validatable validatable && validated.add(value)) validatable.validate();
    }

    private String writeYamlWithComments(Map<String, Object> map, Map<String, List<String>> comments, String header) {
        StringBuilder sb = new StringBuilder();
        if (header != null && !header.isBlank()) {
            for (String line : header.split("\n")) sb.append("# ").append(line).append("\n");
            sb.append("\n");
        }
        writeSection(sb, map, comments, "", 0);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private void writeSection(StringBuilder sb, Map<String, Object> map,
                              Map<String, List<String>> comments, String base, int indent) {
        int i = 0;
        Object previousValue = null;
        for (Map.Entry<?, ?> e : map.entrySet()) {
            String k = mapKeyToYaml(e.getKey());
            Object v = e.getValue();
            String full = base.isEmpty() ? k : base + "." + k;
            List<String> c = comments.get(full);

            if (i > 0 && (isYamlSection(previousValue) || shouldSeparateYamlEntry(v, c))) {
                sb.append("\n");
            }
            i++;
            previousValue = v;

            if (c != null) for (String line : c)
                sb.repeat("  ", indent).append("# ").append(line).append("\n");

            if (v instanceof Map<?, ?> m) {
                sb.repeat("  ", indent).append(formatMapKey(k)).append(":");
                if (m.isEmpty()) {
                    sb.append(" {}\n");
                } else {
                    sb.append("\n");
                    writeSection(sb, (Map<String, Object>) m, comments, full, indent + 1);
                }
                continue;
            }

            if (v instanceof List<?> list) {
                sb.repeat("  ", indent).append(formatMapKey(k)).append(":");
                if (list.isEmpty()) {
                    sb.append(" []\n");
                    continue;
                }
                sb.append("\n");
                for (Object it : list) {
                    if (it instanceof Map<?, ?> m) {
                        writeListMapItem(sb, m, comments, "", indent);
                        continue;
                    }

                    sb.repeat("  ", indent + 1).append("- ");
                    writeValue(sb, it, comments, "", indent + 1);
                }
                continue;
            }

            if (v instanceof String s && s.contains("\n")) {
                sb.repeat("  ", indent).append(formatMapKey(k)).append(": |2\n");
                for (String line : s.split("\n", -1))
                    sb.repeat("  ", indent + 1).append(line).append("\n");
            } else {
                sb.repeat("  ", indent).append(formatMapKey(k)).append(": ").append(formatScalar(v)).append("\n");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void writeValue(StringBuilder sb, Object v,
                            Map<String, List<String>> comments, String base, int indent) {
        switch (v) {
            case null -> {
                sb.append("null").append("\n");
                return;
            }
            case String s -> {
                if (s.contains("\n")) {
                    sb.append("|2\n");
                    for (String line : s.split("\n", -1))
                        sb.repeat("  ", indent + 1).append(line).append("\n");
                } else {
                    sb.append(formatScalar(s)).append("\n");
                }
                return;
            }
            case Map<?, ?> m -> {
                if (m.isEmpty()) {
                    sb.append("{}").append("\n");
                    return;
                }
                sb.append("\n");
                writeSection(sb, (Map<String, Object>) m, comments, base, indent + 1);
                return;
            }
            case List<?> list -> {
                if (list.isEmpty()) {
                    sb.append("[]").append("\n");
                    return;
                }
                sb.append("\n");
                for (Object it : list) {
                    if (it instanceof Map<?, ?> sub) {
                        writeListMapItem(sb, sub, comments, base, indent);
                    } else {
                        sb.repeat("  ", indent + 1).append("- ");
                        writeValue(sb, it, comments, base, indent + 1);
                    }
                }
                return;
            }
            default -> {
            }
        }
        sb.append(formatScalar(v)).append("\n");
    }

    private void writeListMapItem(StringBuilder sb, Map<?, ?> map,
                                  Map<String, List<String>> comments, String base, int indent) {
        if (map.isEmpty()) {
            sb.repeat("  ", indent + 1).append("- {}\n");
            return;
        }

        Iterator<? extends Map.Entry<?, ?>> iterator = map.entrySet().iterator();
        Map.Entry<?, ?> first = iterator.next();

        sb.repeat("  ", indent + 1)
                .append("- ")
                .append(formatMapKey(mapKeyToYaml(first.getKey()))).append(": ");
        writeValue(sb, first.getValue(), comments, base, indent + 1);

        while (iterator.hasNext()) {
            Map.Entry<?, ?> entry = iterator.next();
            sb.repeat("  ", indent + 2)
                    .append(formatMapKey(mapKeyToYaml(entry.getKey()))).append(": ");
            writeValue(sb, entry.getValue(), comments, base, indent + 2);
        }
    }

    private String formatScalar(Object v) {
        if (v == null) return "null";
        if (v instanceof String s) {
            String out = s.replace("\\", "\\\\").replace("\"", "\\\"");
            return "\"" + out + "\"";
        }
        return String.valueOf(v);
    }

    private static String formatMapKey(String k) {
        if (k == null) return "null";
        boolean simple = k.matches("^[A-Za-z0-9_.-]+$")
                && !k.startsWith("*")
                && !k.startsWith("-")
                && !k.startsWith("?")
                && !k.startsWith("&")
                && !k.startsWith("!");
        if (simple) return k;
        String escaped = k.replace("'", "''");
        return "'" + escaped + "'";
    }

    public static Component fmt(Component template, Map<String, ?> vars) {
        return fmt(template, vars, true);
    }

    public static Component fmt(Component template, Map<String, ?> vars, boolean keepColors) {
        if (template == null || vars == null || vars.isEmpty()) return template;
        Component out = template;
        for (var e : vars.entrySet()) {
            String k = e.getKey();
            Object v = e.getValue();

            Component repl;
            String content = v == null ? "" : String.valueOf(v);
            if (keepColors) {
                if (v instanceof Component c) repl = c;
                else if (v instanceof CharSequence cs) repl = parseComponent(cs.toString(), TextMode.AUTO);
                else repl = Component.text(content);
            } else {
                String plain;
                if (v instanceof Component c) {
                    plain = PlainTextComponentSerializer.plainText().serialize(c);
                } else if (v instanceof CharSequence cs) {
                    Component parsed = parseComponent(cs.toString(), TextMode.AUTO);
                    plain = PlainTextComponentSerializer.plainText().serialize(parsed);
                } else {
                    plain = content;
                }
                repl = Component.text(plain);
            }

            out = out.replaceText(TextReplacementConfig.builder()
                    .matchLiteral("{" + k + "}")
                    .replacement(repl)
                    .build());
        }
        return out;
    }

    private static boolean isStatic(Field f) {
        return (f.getModifiers() & Modifier.STATIC) != 0;
    }

    private static boolean isOptionalField(Field f) {
        return f.isAnnotationPresent(Optional.class);
    }

    private static List<Field> orderedConfigFields(Class<?> type, boolean publicOnly) {
        List<Field> fields = new ArrayList<>();
        collectConfigFields(type, publicOnly, fields);
        return fields;
    }

    private static void collectConfigFields(Class<?> type, boolean publicOnly, List<Field> fields) {
        if (type == null || type == Object.class) return;
        collectConfigFields(type.getSuperclass(), publicOnly, fields);
        for (Field field : type.getDeclaredFields()) {
            if (field.isSynthetic()) continue;
            if (publicOnly && !Modifier.isPublic(field.getModifiers())) continue;
            fields.add(field);
        }
    }

    private void exportSection(Object section, String path, Map<String, Object> existing,
                               Map<String, Object> out, Map<String, List<String>> comments, Field field) {
        exportSection(section, path, existing, out, comments, field, false);
    }

    private void exportSection(Object section, String path, Map<String, Object> existing,
                               Map<String, Object> out, Map<String, List<String>> comments, Field field, boolean optionalContext) {
        Comment fc = field.getAnnotation(Comment.class);
        if (fc != null) comments.put(path, Arrays.asList(fc.value()));

        Comment sc = section.getClass().getAnnotation(Comment.class);
        if (sc != null) comments.put(path, Arrays.asList(sc.value()));
        export(section, path, existing, out, comments, optionalContext);
    }

    private static String toSnake(String s) {
        return s.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }

    private static String classNodeName(Class<?> c) {
        Name n = c.getAnnotation(Name.class);
        if (n != null && !n.value().isBlank()) return n.value();
        return c.isAnnotationPresent(Section.class) ? toSnake(c.getSimpleName()) : "";
    }

    private static String fieldNodeName(Field f) {
        Name n = f.getAnnotation(Name.class);
        if (n != null && !n.value().isBlank()) return n.value();
        return toSnake(f.getName());
    }

    private static String rootName(Class<?> c) {
        Name n = c.getAnnotation(Name.class);
        return (n != null) ? n.value() : "";
    }

    private static String keyPath(Field f, String prefix) {
        Path p = f.getAnnotation(Path.class);
        if (p != null) {
            if (p.absolute()) return p.value();
            return (prefix == null || prefix.isBlank()) ? p.value() : prefix + "." + p.value();
        }
        String node = fieldNodeName(f);
        if (prefix == null || prefix.isBlank()) return node;
        return node.startsWith(prefix + ".") ? node : prefix + "." + node;
    }

    private static void omitConfigFields(Field contextField, Map<String, Object> yaml) {
        OmitConfigFields omit = contextField.getAnnotation(OmitConfigFields.class);
        if (omit == null || yaml == null || yaml.isEmpty()) return;
        for (String field : omit.value()) {
            if (field == null || field.isBlank()) continue;
            yaml.remove(field);
            yaml.remove(toSnake(field));
        }
    }

    private static boolean hasPublicFields(Class<?> t) {
        if (t.isEnum()) return false;
        for (Field f : t.getFields()) if (!isStatic(f)) return true;
        return false;
    }

    private static boolean isSectionField(Field f) {
        Class<?> t = f.getType();
        if (t.isEnum()
                || t.isPrimitive()
                || t == String.class
                || Number.class.isAssignableFrom(t)
                || t == Boolean.class
                || t == Character.class
                || t == Component.class
                || Collection.class.isAssignableFrom(t)
                || Map.class.isAssignableFrom(t)) {
            return false;
        }
        return t.isAnnotationPresent(Section.class) || hasPublicFields(t);
    }

    @SuppressWarnings("unchecked")
    private static Object getByPath(Map<String, Object> map, String path) {
        String[] parts = path.split("\\.");
        Map<String, Object> cur = map;
        for (int i = 0; i < parts.length; i++) {
            Object o = cur.get(parts[i]);
            if (i == parts.length - 1) return o;
            if (!(o instanceof Map<?, ?> m)) return null;
            cur = (Map<String, Object>) m;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static void mergeYaml(Map<String, Object> target, Map<String, Object> source) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            Object existing = target.get(entry.getKey());
            Object value = entry.getValue();
            if (existing instanceof Map<?, ?> existingMap && value instanceof Map<?, ?> valueMap) {
                mergeYaml((Map<String, Object>) existingMap, (Map<String, Object>) valueMap);
                continue;
            }
            target.put(entry.getKey(), value);
        }
    }

    private static boolean isEmptyYamlNode(Object value) {
        return switch (value) {
            case null -> true;
            case Map<?, ?> map -> map.isEmpty() || map.values().stream().allMatch(ConfigManager::isEmptyYamlNode);
            case Collection<?> collection -> collection.isEmpty() || collection.stream().allMatch(ConfigManager::isEmptyYamlNode);
            default -> false;
        };
    }

    private static boolean isDefaultFieldValue(Field field, Object value) {
        try {
            Object defaults = newInstance(field.getDeclaringClass());
            field.setAccessible(true);
            return Objects.equals(value, field.get(defaults));
        } catch (RuntimeException ignored) {
            return false;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean shouldSeparateYamlEntry(Object value, List<String> comments) {
        if (comments != null && !comments.isEmpty()) return true;
        return isYamlSection(value);
    }

    private static boolean isYamlSection(Object value) {
        return value instanceof Map<?, ?>;
    }

    @SuppressWarnings("unchecked")
    private static void putByPath(Map<String, Object> map, String path, Object value) {
        String[] parts = path.split("\\.");
        Map<String, Object> cur = map;
        for (int i = 0; i < parts.length - 1; i++) {
            cur = (Map<String, Object>) cur.computeIfAbsent(parts[i], k -> new LinkedHashMap<>());
        }
        cur.put(parts[parts.length - 1], value);
    }

    private Object convertFromYaml(Field f, Object raw, Object fallback) {
        if (raw == null) return fallback;

        Converter<Object> conv = findConverter(f);
        if (conv != null) return conv.read(raw, fallback, f);

        Class<?> t = f.getType();

        if (!List.class.isAssignableFrom(t) && !Map.class.isAssignableFrom(t) && !isSectionField(f))
            return convertSimple(t, raw, f);

        if (List.class.isAssignableFrom(t)) {
            Elements el = f.getAnnotation(Elements.class);
            Type elemType = listElementType(f);
            Class<?> elemCls = rawClass(elemType);
            Converter<Object> elemConv = findConverterForClass(elemCls);

            List<?> input = normalizeToList(raw);

            List<Object> out = new ArrayList<>(input.size());
            for (Object elemRaw : input) {
                if (el != null && el.value() != Object.class && elemRaw instanceof Map<?, ?> map) {
                    out.add(fromMapToPojo(el.value(), map));
                } else if (elemConv != null) {
                    out.add(elemConv.read(elemRaw, null, f));
                } else if (elemType instanceof ParameterizedType) {
                    out.add(convertFromYamlValue(elemType, elemRaw, f));
                } else {
                    out.add(convertSimple(elemCls != null ? elemCls : Object.class, elemRaw, f));
                }
            }
            return out;
        }

        if (Map.class.isAssignableFrom(t)) {
            Elements el = f.getAnnotation(Elements.class);
            if (raw instanceof Map<?, ?> in) {
                Type keyType = mapKeyType(f);
                Type valueType = mapValueType(f);
                Class<?> keyCls = rawClass(keyType);
                Class<?> valueCls = rawClass(valueType);
                Converter<Object> valueConv = findConverterForClass(valueCls);
                Map<Object, Object> out = new LinkedHashMap<>();
                Map<?, ?> fallbackMap = fallback instanceof Map<?, ?> fm ? fm : Map.of();
                for (var e : in.entrySet()) {
                    Object v = e.getValue();
                    Object key = convertSimple(keyCls != null ? keyCls : String.class, e.getKey(), f);
                    Object fallbackValue = fallbackMap.containsKey(key)
                            ? fallbackMap.get(key)
                            : fallbackMap.get(String.valueOf(key));
                    if (el != null && el.value() != Object.class && v instanceof Map<?, ?> m) {
                        out.put(key, fromMapToPojo(el.value(), m));
                    } else if (valueConv != null) {
                        out.put(key, valueConv.read(v, null, f));
                    } else if (valueType instanceof ParameterizedType) {
                        out.put(key, convertFromYamlValue(valueType, v, f, fallbackValue));
                    } else {
                        out.put(key, convertSimple(valueCls != null ? valueCls : Object.class, v, f));
                    }
                }
                return out;
            }
            return Map.of();
        }

        if (raw instanceof Map<?, ?> m) return fromMapToPojo(t, m);

        return fallback;
    }

    private Object convertToYaml(Field f, Object v) {
        if (v == null) return null;

        Converter<Object> conv = findConverter(f);
        if (conv != null) return conv.write(v, f);

        if (f.getType() == Component.class) {
            TextMode mode = f.isAnnotationPresent(TextFormat.class)
                    ? f.getAnnotation(TextFormat.class).value()
                    : TextMode.AUTO;
            return serializeComponent((Component) v, mode);
        }

        if (v.getClass().isEnum()) return ((Enum<?>) v).name();

        if (v instanceof List<?> list) {
            Type elemType = listElementType(f);
            Class<?> elemCls = rawClass(elemType);
            boolean listOfComponent = (elemCls == Component.class);
            Converter<Object> elemConv = findConverterForClass(elemCls);

            TextMode mode = f.isAnnotationPresent(TextFormat.class)
                    ? f.getAnnotation(TextFormat.class).value()
                    : TextMode.AUTO;

            Elements el = f.getAnnotation(Elements.class);
            if (el != null && el.value() != Object.class) {
                List<Object> out = new ArrayList<>(list.size());
                for (Object e : list) {
                    if (e == null) {
                        out.add(null);
                        continue;
                    }
                    out.add(exportPojo(e, f, true));
                }
                return out;
            }

            List<Object> out = new ArrayList<>(list.size());
            for (Object e : list) {
                if (e == null) {
                    out.add(null);
                    continue;
                }
                if (listOfComponent && e instanceof Component c) {
                    out.add(serializeComponent(c, mode));
                } else if (elemConv != null) {
                    out.add(elemConv.write(e, f));
                } else if (e instanceof Enum<?> en) {
                    out.add(en.name());
                } else if (elemType instanceof ParameterizedType) {
                    out.add(convertToYamlValue(elemType, e, f, mode));
                } else if (shouldExportAsPojo(elemCls, e)) {
                    out.add(exportPojo(e, f, true));
                } else {
                    out.add(e);
                }
            }
            return out;
        }

        if (v instanceof Map<?, ?> map) {
            Type valueType = mapValueType(f);
            Class<?> valueCls = rawClass(valueType);
            Converter<Object> valueConv = findConverterForClass(valueCls);
            Elements el = f.getAnnotation(Elements.class);
            TextMode mode = f.isAnnotationPresent(TextFormat.class)
                    ? f.getAnnotation(TextFormat.class).value()
                    : TextMode.AUTO;

            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                Object value = e.getValue();
                if (value == null) {
                    out.put(mapKeyToYaml(e.getKey()), null);
                } else if (el != null && el.value() != Object.class) {
                    out.put(mapKeyToYaml(e.getKey()), exportPojo(value, f, true));
                } else if (valueCls == Component.class && value instanceof Component c) {
                    out.put(mapKeyToYaml(e.getKey()), serializeComponent(c, mode));
                } else if (valueConv != null) {
                    out.put(mapKeyToYaml(e.getKey()), valueConv.write(value, f));
                } else if (value instanceof Enum<?> en) {
                    out.put(mapKeyToYaml(e.getKey()), en.name());
                } else if (valueType instanceof ParameterizedType) {
                    out.put(mapKeyToYaml(e.getKey()), convertToYamlValue(valueType, value, f, mode));
                } else if (shouldExportAsPojo(valueCls, value)) {
                    out.put(mapKeyToYaml(e.getKey()), exportPojo(value, f, true));
                } else {
                    out.put(mapKeyToYaml(e.getKey()), value);
                }
            }
            return out;
        }

        if (shouldExportAsPojo(f.getType(), v)) {
            return exportPojo(v, f, true);
        }

        return v;
    }

    private Object convertToYamlPreserving(Field f, Object value, Object existingRaw) {
        if (existingRaw != null && preservesValue(f, existingRaw, value)) return existingRaw;
        return convertToYaml(f, value);
    }

    private boolean preservesValue(Field f, Object raw, Object value) {
        try {
            return Objects.equals(convertFromYaml(f, raw, null), value);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private Object convertFromYamlValue(Type type, Object raw, Field contextField) {
        return convertFromYamlValue(type, raw, contextField, null);
    }

    private Object convertFromYamlValue(Type type, Object raw, Field contextField, Object fallback) {
        if (raw == null) return null;

        Class<?> cls = rawClass(type);
        if (type instanceof ParameterizedType p && cls != null) {
            Type[] args = p.getActualTypeArguments();
            if (List.class.isAssignableFrom(cls) && args.length == 1) {
                List<?> input = normalizeToList(raw);
                List<Object> out = new ArrayList<>(input.size());
                for (Object elem : input) out.add(convertFromYamlValue(args[0], elem, contextField));
                return out;
            }
            if (Map.class.isAssignableFrom(cls) && args.length == 2 && raw instanceof Map<?, ?> input) {
                Class<?> keyCls = rawClass(args[0]);
                Map<?, ?> fallbackMap = fallback instanceof Map<?, ?> fm ? fm : Map.of();
                Map<Object, Object> out = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : input.entrySet()) {
                    Object key = convertSimple(keyCls != null ? keyCls : String.class, entry.getKey(), contextField);
                    Object fallbackValue = fallbackMap.containsKey(key)
                            ? fallbackMap.get(key)
                            : fallbackMap.get(String.valueOf(key));
                    out.put(key, convertFromYamlValue(args[1], entry.getValue(), contextField, fallbackValue));
                }
                return out;
            }
        }

        Converter<Object> conv = findConverterForClass(cls);
        if (conv != null) return conv.read(raw, null, contextField);
        if (raw instanceof Map<?, ?> m && isConfigPojo(cls)) return fromMapToPojo(cls, m);
        if (isConfigPojo(cls)) return fallback != null ? fallback : newInstance(cls);
        return convertSimple(cls != null ? cls : Object.class, raw, contextField);
    }

    private Object convertToYamlValue(Type type, Object value, Field contextField, TextMode mode) {
        if (value == null) return null;

        Class<?> cls = rawClass(type);
        if (type instanceof ParameterizedType p && cls != null) {
            Type[] args = p.getActualTypeArguments();
            if (List.class.isAssignableFrom(cls) && args.length == 1 && value instanceof List<?> list) {
                List<Object> out = new ArrayList<>(list.size());
                for (Object element : list) out.add(convertToYamlValue(args[0], element, contextField, mode));
                return out;
            }
            if (Map.class.isAssignableFrom(cls) && args.length == 2 && value instanceof Map<?, ?> map) {
                Map<String, Object> out = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    out.put(mapKeyToYaml(entry.getKey()), convertToYamlValue(args[1], entry.getValue(), contextField, mode));
                }
                return out;
            }
        }

        if (cls == Component.class && value instanceof Component c) return serializeComponent(c, mode);

        Converter<Object> conv = findConverterForClass(cls);
        if (conv != null) return conv.write(value, contextField);

        if (value instanceof Enum<?> en) return en.name();

        Class<?> exportType = cls != null && cls != Object.class ? cls : value.getClass();
        if (isConfigPojo(exportType)) {
            return exportPojo(value, contextField, false);
        }
        return value;
    }

    private static Type listElementType(Field f) {
        var gt = f.getGenericType();
        if (gt instanceof ParameterizedType p) {
            var args = p.getActualTypeArguments();
            if (args.length == 1) return args[0];
        }
        return null;
    }

    private static Type mapKeyType(Field f) {
        var gt = f.getGenericType();
        if (gt instanceof ParameterizedType p) {
            var args = p.getActualTypeArguments();
            if (args.length == 2) return args[0];
        }
        return null;
    }

    private static Type mapValueType(Field f) {
        var gt = f.getGenericType();
        if (gt instanceof ParameterizedType p) {
            var args = p.getActualTypeArguments();
            if (args.length == 2) return args[1];
        }
        return null;
    }

    private static Class<?> rawClass(Type type) {
        if (type instanceof Class<?> c) return c;
        if (type instanceof ParameterizedType p && p.getRawType() instanceof Class<?> c) return c;
        return null;
    }

    private static String mapKeyToYaml(Object key) {
        if (key == null) return "null";
        if (key instanceof Enum<?> en) return en.name();
        return String.valueOf(key);
    }

    private static boolean shouldExportAsPojo(Class<?> declaredType, Object value) {
        return declaredType != null
                && declaredType != Object.class
                && declaredType.isInstance(value)
                && isConfigPojo(declaredType);
    }

    private static boolean isConfigPojo(Class<?> type) {
        return type != null
                && !type.isPrimitive()
                && type != String.class
                && !Number.class.isAssignableFrom(type)
                && type != Boolean.class
                && type != Character.class
                && type != Component.class
                && !type.isEnum()
                && !Collection.class.isAssignableFrom(type)
                && !Map.class.isAssignableFrom(type)
                && hasPublicFields(type);
    }

    private static List<?> normalizeToList(Object raw) {
        if (raw instanceof List<?> l) return l;
        if (raw instanceof Map<?, ?> inMap) {
            List<Map.Entry<?, ?>> entries = new ArrayList<>(inMap.entrySet());
            entries.sort((a, b) -> {
                try {
                    int ia = Integer.parseInt(String.valueOf(a.getKey()));
                    int ib = Integer.parseInt(String.valueOf(b.getKey()));
                    return Integer.compare(ia, ib);
                } catch (NumberFormatException e) {
                    return String.valueOf(a.getKey()).compareTo(String.valueOf(b.getKey()));
                }
            });
            List<Object> tmp = new ArrayList<>(entries.size());
            for (var e : entries) tmp.add(e.getValue());
            return tmp;
        }
        return List.of(raw);
    }

    private Object convertSimple(Class<?> t, Object raw, Field contextField) {
        if (raw == null) return null;

        if (t == Component.class) {
            TextMode mode = contextField.isAnnotationPresent(TextFormat.class)
                    ? contextField.getAnnotation(TextFormat.class).value()
                    : TextMode.AUTO;
            return parseComponent(String.valueOf(raw), mode);
        }
        if (t.isEnum()) return parseEnum(t, String.valueOf(raw));
        if (t == String.class) return String.valueOf(raw);
        if (t == int.class || t == Integer.class) return asNumber(raw).intValue();
        if (t == long.class || t == Long.class) return asNumber(raw).longValue();
        if (t == double.class || t == Double.class) return asNumber(raw).doubleValue();
        if (t == float.class || t == Float.class) return asNumber(raw).floatValue();
        if (t == short.class || t == Short.class) return asNumber(raw).shortValue();
        if (t == byte.class || t == Byte.class) return asNumber(raw).byteValue();
        if (t == char.class || t == Character.class) {
            String s = String.valueOf(raw);
            return s.isEmpty() ? '\0' : s.charAt(0);
        }
        if (t == boolean.class || t == Boolean.class)
            return (raw instanceof Boolean b) ? b : Boolean.parseBoolean(String.valueOf(raw));
        if (t == BigDecimal.class) return new BigDecimal(String.valueOf(raw));
        if (t == BigInteger.class) return new BigInteger(String.valueOf(raw));
        if (t == UUID.class) return UUID.fromString(String.valueOf(raw));
        if (t == Duration.class) return Duration.parse(String.valueOf(raw));
        if (t == Locale.class) return Locale.forLanguageTag(String.valueOf(raw));

        if (raw instanceof Map<?, ?> m && hasPublicFields(t)) return fromMapToPojo(t, m);

        return raw;
    }

    private Map<String, Object> exportPojo(Object value, Field contextField, boolean applyOmitFields) {
        Map<String, Object> out = new LinkedHashMap<>();
        export(value, "", Map.of(), out, new LinkedHashMap<>());
        if (applyOmitFields) omitConfigFields(contextField, out);
        return out;
    }

    private Object fromMapToPojo(Class<?> t, Map<?, ?> m) {
        Object inst = newInstance(t);
        Map<String, Object> values = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : m.entrySet()) {
            values.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        inject(values, inst, "");
        return inst;
    }

    private static Component parseComponent(String s, TextMode mode) {
        if (s == null) return Component.empty();
        return switch (mode) {
            case MINIMESSAGE -> MM.deserialize(rewriteHexAmpToMini(s));
            case LEGACY_AMP -> LEGACY_SEC.deserialize(toSectionWithHex(s));
            case PLAIN -> PlainTextComponentSerializer.plainText().deserialize(s);
            case AUTO -> {
                boolean hasMini = looksLikeMini(s);
                boolean hasLegacy = looksLikeLegacy(s);

                if (hasMini && !hasLegacy) yield MM.deserialize(rewriteHexAmpToMini(s));
                if (hasLegacy && !hasMini) yield LEGACY_SEC.deserialize(toSectionWithHex(s));

                if (hasMini) {
                    if (looksLikeMiniStrong(s)) yield MM.deserialize(rewriteHexAmpToMini(s));
                    yield LEGACY_SEC.deserialize(toSectionWithHex(s));
                }
                yield Component.text(s);
            }
        };
    }

    private static boolean looksLikeLegacy(String s) {
        if (s.indexOf('&') >= 0 || s.indexOf('§') >= 0) return true;
        if (HEX_AMP.matcher(s).find()) return true;
        return s.contains("&x&");
    }

    private static boolean looksLikeMini(String s) {
        if (s == null) return false;
        int lt = s.indexOf('<');
        if (lt < 0) return false;
        int gt = s.indexOf('>', lt);
        if (gt < 0) return false;
        return MINI_HEX.matcher(s).find() || MINI_TAG.matcher(s).find();
    }

    private static boolean looksLikeMiniStrong(String s) {
        return s.contains("</")
                || s.matches(".*<(?i:click|hover|url|insertion|keybind|font|gradient|rainbow)(:|\\s|>).*");
    }

    private static String serializeComponent(Component c, TextMode mode) {
        return switch (mode) {
            case MINIMESSAGE -> MM.serialize(c);
            case LEGACY_AMP -> collapseLegacyHex(LEGACY_AMP.serialize(c));
            case PLAIN -> PlainTextComponentSerializer.plainText().serialize(c);
            case AUTO -> {
                boolean needMini = needsMiniMessage(c);
                if (needMini) yield MM.serialize(c);
                yield collapseLegacyHex(LEGACY_AMP.serialize(c));
            }
        };
    }

    private static boolean needsMiniMessage(Component c) {
        if (c.clickEvent() != null || c.hoverEvent() != null) return true;
        for (var child : c.children())
            if (needsMiniMessage(child)) return true;
        return false;
    }

    private static String collapseLegacyHex(String s) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); ) {
            if (i + 1 < s.length() && s.charAt(i) == '&' && (s.charAt(i + 1) == 'x' || s.charAt(i + 1) == 'X')) {
                int j = i + 2, k = 0;
                char[] hex = new char[6];
                boolean ok = true;
                while (k < 6) {
                    if (j + 1 >= s.length() || s.charAt(j) != '&') {
                        ok = false;
                        break;
                    }
                    char h = s.charAt(j + 1);
                    if (!Character.toString(h).matches("[0-9a-fA-F]")) {
                        ok = false;
                        break;
                    }
                    hex[k++] = Character.toUpperCase(h);
                    j += 2;
                }
                if (ok) {
                    out.append("&#").append(hex);
                    i = j;
                    continue;
                }
            }
            out.append(s.charAt(i++));
        }
        return out.toString();
    }

    private static String rewriteHexAmpToMini(String s) {
        Matcher m = HEX_AMP.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String hex = m.group(1);
            m.appendReplacement(sb, "<#" + hex + ">");
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String toSectionWithHex(String s) {
        String r = s.replace('&', '§');
        Matcher m = HEX_AMP.matcher(r);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String hex = m.group(1);
            StringBuilder seq = new StringBuilder("§x");
            for (char ch : hex.toCharArray()) {
                seq.append('§').append(Character.toLowerCase(ch));
            }
            m.appendReplacement(sb, seq.toString());
        }
        m.appendTail(sb);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static <E extends Enum<E>> E parseEnum(Class<?> enumClass, String name) {
        Class<E> typed = (Class<E>) enumClass.asSubclass(Enum.class);
        String n = name.trim();
        try {
            return Enum.valueOf(typed, n);
        } catch (IllegalArgumentException ex) {
            return Enum.valueOf(typed, n.toUpperCase());
        }
    }

    private static Number asNumber(Object raw) {
        if (raw instanceof Number n) return n;
        try {
            return Double.parseDouble(String.valueOf(raw));
        } catch (Exception e) {
            return 0;
        }
    }

    private static <T> T newInstance(Class<T> type) {
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Config class must have a no-args constructor: " + type.getName(), e);
        }
    }

    private static <T> T copyConfigInstance(T source, Class<T> type) {
        return type.cast(copyConfigValue(source, source.getClass(), null, new IdentityHashMap<>()));
    }

    private static Object copyConfigValue(
            Object value,
            Type declaredType,
            Field contextField,
            IdentityHashMap<Object, Object> copies
    ) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean
                || value instanceof Character || value instanceof Enum<?> || value instanceof UUID
                || value instanceof Duration || value instanceof Component) {
            return value;
        }
        Object known = copies.get(value);
        if (known != null) return known;
        Class<?> declaredClass = rawClass(declaredType);
        Converter<Object> converter = findConverterForClass(declaredClass);
        if (converter == null && (declaredClass == null || declaredClass != value.getClass())) {
            converter = findConverterForClass(value.getClass());
        }
        if (converter != null) {
            Object copy = converter.read(converter.write(value, contextField), null, contextField);
            copies.put(value, copy);
            return copy;
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            Object copy = Array.newInstance(value.getClass().getComponentType(), length);
            copies.put(value, copy);
            Type componentType = value.getClass().getComponentType();
            for (int i = 0; i < length; i++) {
                Array.set(copy, i, copyConfigValue(Array.get(value, i), componentType, contextField, copies));
            }
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            copies.put(value, copy);
            Type elementType = collectionElementType(declaredType);
            for (Object element : list) copy.add(copyConfigValue(element, elementType, contextField, copies));
            return copy;
        }
        if (value instanceof Set<?> set) {
            Set<Object> copy = new LinkedHashSet<>();
            copies.put(value, copy);
            Type elementType = collectionElementType(declaredType);
            for (Object element : set) copy.add(copyConfigValue(element, elementType, contextField, copies));
            return copy;
        }
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> copy = new LinkedHashMap<>();
            copies.put(value, copy);
            Type keyType = mapTypeArgument(declaredType, 0);
            Type valueType = mapTypeArgument(declaredType, 1);
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                copy.put(copyConfigValue(entry.getKey(), keyType, contextField, copies),
                        copyConfigValue(entry.getValue(), valueType, contextField, copies));
            }
            return copy;
        }
        if (isConfigPojo(value.getClass())) {
            Object copy = newInstance(value.getClass());
            copies.put(value, copy);
            for (Field field : orderedConfigFields(value.getClass(), true)) {
                if (isStatic(field)) continue;
                try {
                    Object fieldValue = field.get(value);
                    Converter<Object> fieldConverter = findConverter(field);
                    Object fieldCopy = fieldConverter == null
                            ? copyConfigValue(fieldValue, field.getGenericType(), field, copies)
                            : fieldValue == null ? null
                            : fieldConverter.read(fieldConverter.write(fieldValue, field), null, field);
                    if (fieldValue != null && fieldCopy != null) copies.put(fieldValue, fieldCopy);
                    field.set(copy, fieldCopy);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException("Unable to copy config field: " + field.getName(), e);
                }
            }
            return copy;
        }
        return value;
    }

    private static Type collectionElementType(Type type) {
        if (type instanceof ParameterizedType parameterized) {
            Type[] arguments = parameterized.getActualTypeArguments();
            if (arguments.length == 1) return arguments[0];
        }
        return Object.class;
    }

    private static Type mapTypeArgument(Type type, int index) {
        if (type instanceof ParameterizedType parameterized) {
            Type[] arguments = parameterized.getActualTypeArguments();
            if (arguments.length == 2) return arguments[index];
        }
        return Object.class;
    }

    public static Component parseDynamic(String s) {
        return parseComponent(s, TextMode.AUTO);
    }
}
