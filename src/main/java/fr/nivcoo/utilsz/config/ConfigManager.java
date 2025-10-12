package fr.nivcoo.utilsz.config;

import fr.nivcoo.utilsz.config.annotations.*;
import fr.nivcoo.utilsz.config.text.TextMode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ConfigManager {
    private final File dataFolder;
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_AMP = LegacyComponentSerializer.legacyAmpersand();
    private static final LegacyComponentSerializer LEGACY_SEC = LegacyComponentSerializer.legacySection();
    private static final Pattern HEX_AMP = Pattern.compile("&#([A-Fa-f0-9]{6})");

    public ConfigManager(File dataFolder) {
        this.dataFolder = Objects.requireNonNull(dataFolder);
    }

    public <T> T load(Class<T> cfgClass) {
        ConfigRoot root = cfgClass.getAnnotation(ConfigRoot.class);
        if (root == null) throw new IllegalArgumentException("Missing @ConfigRoot on " + cfgClass.getName());

        File file = new File(dataFolder, root.file());
        Map<String, Object> existing = loadYaml(file);

        T instance = newInstance(cfgClass);
        inject(existing, instance, rootName(cfgClass));
        validate(instance);

        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, List<String>> comments = new LinkedHashMap<>();
        export(instance, rootName(cfgClass), out, comments);

        String text = writeYamlWithComments(out, comments, root.header());
        saveText(file, text);
        return instance;
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
        if (parent != null && !parent.exists()) {
            if (!parent.mkdirs()) {
                throw new IllegalStateException("Unable to create config directory: " + parent);
            }
        }

        try (var writer = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
            writer.write(text);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to save config file: " + f, e);
        }
    }


    private void inject(Map<String, Object> src, Object bean, String prefix) {
        for (Field f : bean.getClass().getFields()) {
            if (isStatic(f)) continue;
            String path = keyPath(f, prefix);
            try {
                if (isSectionField(f)) {
                    Object sec = f.get(bean);
                    if (sec == null) {
                        sec = newInstance(f.getType());
                        f.set(bean, sec);
                    }
                    inject(src, sec, sectionPrefix(f.getType(), path));
                } else {
                    Object raw = getByPath(src, path);
                    Object val = convertFromYaml(f, raw, f.get(bean));
                    if (val != null) f.set(bean, val);
                }
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
                    if (sec != null) export(sec, sectionPrefix(f.getType(), path), out, comments);
                } else {
                    Object v = f.get(bean);
                    Object yamlVal = convertToYaml(f, v);
                    putByPath(out, path, yamlVal);
                    Comment c = f.getAnnotation(Comment.class);
                    if (c != null) comments.put(path, List.of(c.value()));
                }
            } catch (Exception e) {
                throw new RuntimeException("Export failed for " + path + ": " + e.getMessage(), e);
            }
        }
    }

    private void validate(Object bean) {
        for (Field f : bean.getClass().getFields()) {
            if (isStatic(f)) continue;
            try {
                Object v = f.get(bean);
                if (isSectionField(f) && v != null) {
                    validate(v);
                    continue;
                }
                if (f.isAnnotationPresent(NotNull.class) && v == null)
                    throw new IllegalArgumentException("Field " + f.getName() + " is @NotNull but null");
                Range r = f.getAnnotation(Range.class);
                if (r != null && v instanceof Number n) {
                    double d = n.doubleValue();
                    if (d < r.min() || d > r.max())
                        throw new IllegalArgumentException("Field " + f.getName() + " out of range [" + r.min() + "," + r.max() + "]: " + d);
                }
            } catch (RuntimeException re) {
                throw re;
            } catch (Exception e) {
                throw new RuntimeException(e);
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
    private void writeSection(StringBuilder sb, Map<String, Object> map, Map<String, List<String>> comments, String base, int indent) {
        List<String> keys = new ArrayList<>(map.keySet());
        Collections.sort(keys);
        for (String k : keys) {
            Object v = map.get(k);
            String full = base.isEmpty() ? k : base + "." + k;

            List<String> c = comments.get(full);
            if (c != null) for (String line : c) sb.append("  ".repeat(indent)).append("# ").append(line).append("\n");

            if (v instanceof Map<?, ?> m) {
                sb.append("  ".repeat(indent)).append(k).append(":\n");
                writeSection(sb, (Map<String, Object>) m, comments, full, indent + 1);
            } else if (v instanceof List<?> list) {
                sb.append("  ".repeat(indent)).append(k).append(":\n");
                for (Object it : list) {
                    sb.append("  ".repeat(indent + 1)).append("- ").append(formatScalar(it)).append("\n");
                }
            } else {
                sb.append("  ".repeat(indent)).append(k).append(": ").append(formatScalar(v)).append("\n");
            }
        }
    }

    private String formatScalar(Object v) {
        if (v == null) return "null";
        if (v instanceof String s) {
            boolean needsQuotes = s.isEmpty() || s.matches(".*[:#\\-{}\\[\\],&*?]|^\\s|\\s$|^\\d+$|^(true|false|null)$");
            String out = s.replace("\"", "\\\"");
            return needsQuotes ? "\"" + out + "\"" : out;
        }
        return String.valueOf(v);
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

    private static String sectionPrefix(Class<?> type, String base) {
        String node = classNodeName(type);
        if (node.isBlank()) return base;
        if (base == null || base.isBlank()) return node;
        return base + "." + node;
    }

    private static String keyPath(Field f, String prefix) {
        Path p = f.getAnnotation(Path.class);
        if (p != null) {
            if (p.absolute()) return p.value();
            return (prefix == null || prefix.isBlank())
                    ? p.value()
                    : prefix + "." + p.value();
        }
        String node = fieldNodeName(f);
        if (prefix == null || prefix.isBlank()) return node;
        return node.startsWith(prefix + ".") ? node : prefix + "." + node;
    }

    private static boolean hasPublicFields(Class<?> t) {
        for (Field f : t.getFields())
            if (!isStatic(f)) return true;
        return false;
    }

    private static boolean isSectionField(Field f) {
        return f.getType().isAnnotationPresent(Section.class) || hasPublicFields(f.getType());
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


    @SuppressWarnings({"unchecked"})
    private Object convertFromYaml(Field f, Object raw, Object fallback) {
        if (raw == null) return fallback;

        Class<?> t = f.getType();

        if (t == Component.class) {
            TextMode mode = f.isAnnotationPresent(TextFormat.class)
                    ? f.getAnnotation(TextFormat.class).value()
                    : TextMode.AUTO;
            return parseComponent(String.valueOf(raw), mode);
        }

        if (t.isEnum()) {
            return parseEnum((Class<? extends Enum<?>>) t, String.valueOf(raw));
        }

        if (t == String.class) return String.valueOf(raw);
        if (t == int.class || t == Integer.class) return asNumber(raw).intValue();
        if (t == long.class || t == Long.class) return asNumber(raw).longValue();
        if (t == double.class || t == Double.class) return asNumber(raw).doubleValue();
        if (t == boolean.class || t == Boolean.class)
            return (raw instanceof Boolean b) ? b : Boolean.parseBoolean(String.valueOf(raw));

        if (List.class.isAssignableFrom(t))
            return (raw instanceof List<?> l) ? new ArrayList<>(l) : List.of(String.valueOf(raw));
        if (Map.class.isAssignableFrom(t))
            return (raw instanceof Map<?, ?> m) ? new LinkedHashMap<>(m) : Map.of();

        return fallback;
    }

    @SuppressWarnings("unchecked")
    private static <E extends Enum<E>> E parseEnum(Class<? extends Enum<?>> enumType, String name) {
        Class<E> typed = (Class<E>) enumType.asSubclass(Enum.class);
        String n = name.trim();
        try {
            return Enum.valueOf(typed, n);
        } catch (IllegalArgumentException e) {
            return Enum.valueOf(typed, n.toUpperCase());
        }
    }


    private Object convertToYaml(Field f, Object v) {
        if (v == null) return null;

        if (f.getType() == Component.class) {
            TextMode mode = f.isAnnotationPresent(TextFormat.class)
                    ? f.getAnnotation(TextFormat.class).value()
                    : TextMode.AUTO;
            return serializeComponent((Component) v, mode);
        }
        if (v.getClass().isEnum()) return ((Enum<?>) v).name();
        return v;
    }

    private static Number asNumber(Object raw) {
        if (raw instanceof Number n) return n;
        try {
            return Double.parseDouble(String.valueOf(raw));
        } catch (Exception e) {
            return 0;
        }
    }


    private static Component parseComponent(String s, TextMode mode) {
        if (s == null) return Component.empty();
        return switch (mode) {
            case MINIMESSAGE -> MM.deserialize(rewriteHexAmpToMini(s));
            case LEGACY_AMP -> LEGACY_SEC.deserialize(toSectionWithHex(s));
            case PLAIN -> Component.text(s);
            case AUTO -> {
                String mmCand = rewriteHexAmpToMini(s);
                boolean looksMini = looksLikeMini(mmCand);
                boolean looksLegacy = s.indexOf('&') >= 0 || s.indexOf('§') >= 0 || s.contains("&#");
                if (looksMini) yield MM.deserialize(mmCand);
                if (looksLegacy) yield LEGACY_SEC.deserialize(toSectionWithHex(s));
                yield Component.text(s);
            }
        };
    }

    private static String serializeComponent(Component c, TextMode mode) {
        return switch (mode) {
            case MINIMESSAGE, AUTO -> MM.serialize(c);
            case LEGACY_AMP -> LEGACY_AMP.serialize(c);
            case PLAIN -> PlainTextComponentSerializer.plainText().serialize(c);
        };
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

    private static boolean looksLikeMini(String s) {
        return s.contains("<#") || s.contains("</") || s.contains("<bold>") || s.contains("<gradient");
    }

    private static <T> T newInstance(Class<T> type) {
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Config class must have a no-args constructor", e);
        }
    }
}
