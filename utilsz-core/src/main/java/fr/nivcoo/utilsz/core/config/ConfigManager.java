package fr.nivcoo.utilsz.core.config;

import fr.nivcoo.utilsz.core.config.annotations.*;
import fr.nivcoo.utilsz.core.config.text.TextMode;
import fr.nivcoo.utilsz.core.config.validation.Validatable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ConfigManager {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_AMP = LegacyComponentSerializer.legacyAmpersand();
    private static final LegacyComponentSerializer LEGACY_SEC = LegacyComponentSerializer.legacySection();
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
        File file = new File(dataFolder, relativePath);
        Map<String, Object> existing = loadYaml(file);

        T instance = newInstance(cfgClass);
        inject(existing, instance, rootName(cfgClass));
        validate(instance, rootName(cfgClass));

        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, List<String>> comments = new LinkedHashMap<>();
        export(instance, rootName(cfgClass), out, comments);

        if (shouldSaveOnLoad(cfgClass)) {
            Comment rootC = cfgClass.getAnnotation(Comment.class);
            String header = (rootC != null) ? String.join("\n", rootC.value()) : null;
            saveText(file, writeYamlWithComments(out, comments, header));
        }
        return instance;
    }

    private static boolean shouldSaveOnLoad(Class<?> cfgClass) {
        SaveOnLoad an = cfgClass.getAnnotation(SaveOnLoad.class);
        return an == null || an.value();
    }

    public <T> List<T> loadAll(String relativeDir, Class<T> cfgClass) {
        return loadAll(relativeDir, cfgClass, f -> {
            String n = f.getName().toLowerCase(Locale.ROOT);
            return n.endsWith(".yml") || n.endsWith(".yaml");
        });
    }

    public <T> List<T> loadAll(String relativeDir, Class<T> cfgClass, Predicate<File> filter) {
        File dir = new File(dataFolder, relativeDir);
        if (!dir.exists() && !dir.mkdirs())
            throw new IllegalStateException("Unable to create directory: " + dir);
        File[] files = dir.listFiles(f -> f.isFile() && filter.test(f));
        if (files == null || files.length == 0) return List.of();

        List<T> list = new ArrayList<>(files.length);
        for (File f : files) list.add(load(pathRelativeToData(f), cfgClass));
        return list;
    }

    private String pathRelativeToData(File f) {
        return dataFolder.toPath().relativize(f.toPath()).toString().replace('\\', '/');
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadYaml(File f) {
        if (!f.exists()) return new LinkedHashMap<>();
        try (var r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
            Object o = new Yaml().load(r);
            if (o instanceof Map<?, ?> m) return new LinkedHashMap<>((Map<String, Object>) m);
            return new LinkedHashMap<>();
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
        for (Field f : bean.getClass().getDeclaredFields()) {
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

    private void export(Object bean, String prefix, Map<String, Object> out, Map<String, List<String>> comments) {
        for (Field f : bean.getClass().getFields()) {
            if (isStatic(f)) continue;
            String path = keyPath(f, prefix);
            try {
                if (isSectionField(f)) {
                    Object sec = f.get(bean);
                    if (sec != null) {
                        Comment fc = f.getAnnotation(Comment.class);
                        if (fc != null) comments.put(path, Arrays.asList(fc.value()));

                        Comment sc = sec.getClass().getAnnotation(Comment.class);
                        if (sc != null) comments.put(path, Arrays.asList(sc.value()));
                        export(sec, path, out, comments);
                    }
                } else {
                    Object v = f.get(bean);
                    Object yamlVal = convertToYaml(f, v);
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
        for (Field f : bean.getClass().getFields()) {
            if (isStatic(f)) continue;
            String path = keyPath(f, prefix);
            try {
                Object v = f.get(bean);

                if (isSectionField(f) && v != null) {
                    validate(v, path);
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

                if (v instanceof Validatable vd) vd.validate();

            } catch (RuntimeException re) {
                throw re;
            } catch (Exception e) {
                throw new RuntimeException("Validate failed for " + path + ": " + e.getMessage(), e);
            }
        }
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
        for (Map.Entry<String, Object> e : map.entrySet()) {
            String k = e.getKey();
            Object v = e.getValue();
            String full = base.isEmpty() ? k : base + "." + k;

            if (i > 0 && v instanceof Map) sb.append("\n");
            i++;

            List<String> c = comments.get(full);
            if (c != null) for (String line : c)
                sb.append("  ".repeat(indent)).append("# ").append(line).append("\n");

            if (v instanceof Map<?, ?> m) {
                sb.append("  ".repeat(indent)).append(formatMapKey(k)).append(":");
                if (m.isEmpty()) {
                    sb.append(" {}\n");
                } else {
                    sb.append("\n");
                    writeSection(sb, (Map<String, Object>) m, comments, full, indent + 1);
                }
                continue;
            }

            if (v instanceof List<?> list) {
                sb.append("  ".repeat(indent)).append(formatMapKey(k)).append(":");
                if (list.isEmpty()) {
                    sb.append(" []\n");
                    continue;
                }
                sb.append("\n");
                for (Object it : list) {
                    if (it instanceof Map<?, ?> mm) {
                        Map<String, Object> m = (Map<String, Object>) mm;
                        if (m.isEmpty()) {
                            sb.append("  ".repeat(indent + 1)).append("- {}\n");
                        } else {
                            Iterator<Map.Entry<String, Object>> itr = m.entrySet().iterator();
                            Map.Entry<String, Object> first = itr.next();

                            sb.append("  ".repeat(indent + 1))
                                    .append("- ")
                                    .append(formatMapKey(first.getKey())).append(": ");
                            writeValue(sb, first.getValue(), comments, "", indent + 1);

                            while (itr.hasNext()) {
                                Map.Entry<String, Object> me = itr.next();
                                sb.append("  ".repeat(indent + 2))
                                        .append(formatMapKey(me.getKey())).append(": ");
                                writeValue(sb, me.getValue(), comments, "", indent + 2);
                            }
                        }
                        continue;
                    }

                    sb.append("  ".repeat(indent + 1)).append("- ");
                    writeValue(sb, it, comments, "", indent + 1);
                }
                continue;
            }

            if (v instanceof String s && s.contains("\n")) {
                sb.append("  ".repeat(indent)).append(formatMapKey(k)).append(": |\n");
                for (String line : s.split("\n", -1))
                    sb.append("  ".repeat(indent + 1)).append(line).append("\n");
            } else {
                sb.append("  ".repeat(indent)).append(formatMapKey(k)).append(": ").append(formatScalar(v)).append("\n");
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
                    sb.append("|\n");
                    for (String line : s.split("\n", -1))
                        sb.append("  ".repeat(indent + 1)).append(line).append("\n");
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
                    if (it instanceof Map<?, ?> mm) {
                        Map<String, Object> sub = (Map<String, Object>) mm;
                        if (sub.isEmpty()) {
                            sb.append("  ".repeat(indent + 1)).append("- {}\n");
                        } else {
                            Iterator<Map.Entry<String, Object>> itr = sub.entrySet().iterator();
                            Map.Entry<String, Object> first = itr.next();

                            sb.append("  ".repeat(indent + 1))
                                    .append("- ")
                                    .append(formatMapKey(first.getKey())).append(": ");
                            writeValue(sb, first.getValue(), comments, base, indent + 1);

                            while (itr.hasNext()) {
                                Map.Entry<String, Object> me = itr.next();
                                sb.append("  ".repeat(indent + 2))
                                        .append(formatMapKey(me.getKey())).append(": ");
                                writeValue(sb, me.getValue(), comments, base, indent + 2);
                            }
                        }
                    } else {
                        sb.append("  ".repeat(indent + 1)).append("- ");
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

    private String formatScalar(Object v) {
        if (v == null) return "null";
        if (v instanceof String s) {
            String out = s.replace("\"", "\\\"");
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
        return (f.getModifiers() & java.lang.reflect.Modifier.STATIC) != 0;
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
                || java.util.Collection.class.isAssignableFrom(t)
                || java.util.Map.class.isAssignableFrom(t)) {
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
            Class<?> elemCls = listElementClass(f);
            Converter<Object> elemConv = findConverterForClass(elemCls);

            List<?> input = normalizeToList(raw);

            List<Object> out = new ArrayList<>(input.size());
            for (Object elemRaw : input) {
                if (el != null && el.value() != Object.class && elemRaw instanceof Map<?, ?> map) {
                    out.add(fromMapToPojo(el.value(), map));
                } else if (elemConv != null) {
                    out.add(elemConv.read(elemRaw, null, f));
                } else {
                    out.add(convertSimple(elemCls != null ? elemCls : Object.class, elemRaw, f));
                }
            }
            return out;
        }

        if (Map.class.isAssignableFrom(t)) {
            Elements el = f.getAnnotation(Elements.class);
            if (el != null && raw instanceof Map<?, ?> in) {
                Map<String, Object> out = new LinkedHashMap<>();
                for (var e : in.entrySet()) {
                    Object v = e.getValue();
                    if (v instanceof Map<?, ?> m) out.put(String.valueOf(e.getKey()), fromMapToPojo(el.value(), m));
                    else out.put(String.valueOf(e.getKey()), v);
                }
                return out;
            }
            return (raw instanceof Map<?, ?> m) ? new LinkedHashMap<>(m) : Map.of();
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
            Class<?> elemCls = listElementClass(f);
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
                    Map<String, Object> m = new LinkedHashMap<>();
                    export(e, "", m, new LinkedHashMap<>());
                    out.add(m);
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
                } else if (e instanceof Enum<?> en) {
                    out.add(en.name());
                } else if (elemConv != null) {
                    out.add(elemConv.write(e, f));
                } else {
                    out.add(e);
                }
            }
            return out;
        }

        return v;
    }

    private static Class<?> listElementClass(Field f) {
        var gt = f.getGenericType();
        if (gt instanceof ParameterizedType p) {
            var args = p.getActualTypeArguments();
            if (args.length == 1 && args[0] instanceof Class<?> c) return c;
        }
        return null;
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

    @SuppressWarnings("unchecked")
    private Object fromMapToPojo(Class<?> t, Map<?, ?> m) {
        Object inst = newInstance(t);
        inject(new LinkedHashMap<>((Map<String, Object>) m), inst, "");
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

    public static Component parseDynamic(String s) {
        return parseComponent(s, TextMode.AUTO);
    }
}
