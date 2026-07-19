package fr.nivcoo.utilsz.core.config;

import fr.nivcoo.utilsz.core.config.annotations.Comment;
import fr.nivcoo.utilsz.core.config.annotations.Optional;
import fr.nivcoo.utilsz.core.config.annotations.Range;
import fr.nivcoo.utilsz.core.config.annotations.Required;
import fr.nivcoo.utilsz.core.config.annotations.SaveOnLoad;
import fr.nivcoo.utilsz.core.config.annotations.Section;
import fr.nivcoo.utilsz.core.config.annotations.TextFormat;
import fr.nivcoo.utilsz.core.config.annotations.WithConverter;
import fr.nivcoo.utilsz.core.config.text.TextMode;
import fr.nivcoo.utilsz.core.config.validation.Validatable;
import fr.nivcoo.utilsz.core.conversion.Converter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void loadCreatesDefaultsWithNestedGenericsAndComments() throws Exception {
        ConfigManager manager = manager();

        ComplexConfig config = manager.load("config.yml", ComplexConfig.class);
        String yaml = Files.readString(tempDir.resolve("config.yml"), StandardCharsets.UTF_8);

        assertEquals("lobby", config.serverName);
        assertEquals("edenplayers", config.discoveryService);
        assertEquals(List.of(new Reward("MONEY", 100)), config.rewards);
        assertEquals(new Reward("XP", 25), config.levels.get(1));
        assertEquals(List.of(new Reward("KEY", 2)), config.grouped.get("vote"));
        assertEquals(Duration.ofSeconds(30), config.timeout);
        assertEquals(Locale.FRANCE, config.locale);
        assertEquals("Hello", PlainTextComponentSerializer.plainText().serialize(config.displayName));
        assertTrue(yaml.contains("# Root header"));
        assertTrue(yaml.contains("# Server id"));
        assertTrue(yaml.contains("routing:"));
        assertTrue(yaml.contains("discovery:"));
        assertTrue(yaml.contains("service: \"edenplayers\""));
        assertTrue(yaml.contains("levels:"));
        assertTrue(yaml.contains("1:"));
        assertFalse(yaml.contains("optional:"));
    }

    @Test
    void loadExistingYamlInjectsListsMapsConvertersAndComponents() throws Exception {
        Files.writeString(tempDir.resolve("config.yml"), """
                server_name: "survival"
                routing:
                  discovery:
                    service: "websitelinkz"
                nested:
                  enabled: false
                  interval_seconds: 12
                rewards:
                  - type: "XP"
                    amount: 40
                levels:
                  2:
                    type: "MONEY"
                    amount: 250
                grouped:
                  vote:
                    - type: "KEY"
                      amount: 3
                timeout: "PT45S"
                locale: "en-US"
                display_name: "&aBonjour"
                uuid: "00000000-0000-0000-0000-000000000001"
                converted: "prefix:value"
                optional:
                  note: "configured"
                """, StandardCharsets.UTF_8);

        ComplexConfig config = manager().load("config.yml", ComplexConfig.class);
        String yaml = Files.readString(tempDir.resolve("config.yml"), StandardCharsets.UTF_8);

        assertEquals("survival", config.serverName);
        assertEquals("websitelinkz", config.discoveryService);
        assertFalse(config.nested.enabled);
        assertEquals(12, config.nested.intervalSeconds);
        assertEquals(List.of(new Reward("XP", 40)), config.rewards);
        assertEquals(new Reward("MONEY", 250), config.levels.get(2));
        assertEquals(List.of(new Reward("KEY", 3)), config.grouped.get("vote"));
        assertEquals(Duration.ofSeconds(45), config.timeout);
        assertEquals(Locale.forLanguageTag("en-US"), config.locale);
        assertEquals(UUID.fromString("00000000-0000-0000-0000-000000000001"), config.uuid);
        assertEquals(new ConvertedValue("value"), config.converted);
        assertEquals("Bonjour", PlainTextComponentSerializer.plainText().serialize(config.displayName));
        assertEquals("configured", config.optional.note);
        assertTrue(yaml.contains("optional:"));
        assertTrue(yaml.contains("note: \"configured\""));
    }

    @Test
    void validationReportsRequiredAndRangeErrors() {
        ConfigManager manager = manager();

        IllegalArgumentException required = assertThrows(IllegalArgumentException.class,
                () -> manager.load("required.yml", RequiredConfig.class));
        IllegalArgumentException range = assertThrows(IllegalArgumentException.class,
                () -> manager.load("range.yml", RangeConfig.class));

        assertTrue(required.getMessage().contains("@Required"));
        assertTrue(range.getMessage().contains("out of range"));
    }

    @Test
    void validatableRunsExactlyOnceAcrossTheConfigurationGraph() {
        ValidationGraphConfig config = manager().load("validation-graph.yml", ValidationGraphConfig.class);

        assertEquals(1, config.validationCount());
        assertEquals(1, config.section.validationCount());
        assertEquals(1, config.direct.validationCount());
        assertEquals(1, config.list.getFirst().validationCount());
        assertEquals(1, config.list.getFirst().section.validationCount());
        assertEquals(1, config.iterableValue.validationCount());
        assertEquals(1, config.map.get("map").validationCount());
        assertEquals(1, config.map.get("map").section.validationCount());
        assertEquals(1, config.array[0].validationCount());
        assertEquals(1, config.array[0].section.validationCount());
        assertEquals(1, config.shared.validationCount());
        assertEquals(1, config.shared.section.validationCount());
    }

    @Test
    void validatesAnnotationsInsidePojoContainerElements() {
        IllegalArgumentException list = assertThrows(IllegalArgumentException.class,
                () -> manager().load("invalid-list-element.yml", InvalidListElementConfig.class));
        IllegalArgumentException map = assertThrows(IllegalArgumentException.class,
                () -> manager().load("invalid-map-element.yml", InvalidMapElementConfig.class));
        IllegalArgumentException array = assertThrows(IllegalArgumentException.class,
                () -> manager().load("invalid-array-element.yml", InvalidArrayElementConfig.class));

        assertTrue(list.getMessage().contains("entries[0].name"));
        assertTrue(list.getMessage().contains("@Required"));
        assertTrue(map.getMessage().contains("entries[premium].amount"));
        assertTrue(map.getMessage().contains("out of range"));
        assertTrue(array.getMessage().contains("entries[0].amount"));
        assertTrue(array.getMessage().contains("out of range"));
    }

    @Test
    void invalidValidatableConfigurationDoesNotRewriteTheFile() throws Exception {
        Path file = tempDir.resolve("invalid.yml");
        String original = "valid: false\nunknown: \"preserved\"\n";
        Files.writeString(file, original, StandardCharsets.UTF_8);

        assertThrows(IllegalArgumentException.class,
                () -> manager().load("invalid.yml", InvalidValidatableConfig.class));

        assertEquals(original, Files.readString(file, StandardCharsets.UTF_8));
    }

    @Test
    void saveOnLoadFalseDoesNotRewriteExistingFile() throws Exception {
        Path file = tempDir.resolve("manual.yml");
        String original = "name: \"custom\"\n# keep this exact file\n";
        Files.writeString(file, original, StandardCharsets.UTF_8);

        ManualConfig config = manager().load("manual.yml", ManualConfig.class);

        assertEquals("custom", config.name);
        assertEquals(original, Files.readString(file, StandardCharsets.UTF_8));
    }

    @Test
    void loadAllLoadsYamlFilesWithFilter() throws Exception {
        Path dir = tempDir.resolve("configs");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("one.yml"), "name: \"one\"\n", StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("two.yaml"), "name: \"two\"\n", StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("ignored.txt"), "name: \"ignored\"\n", StandardCharsets.UTF_8);

        List<SimpleNamedConfig> configs = manager().loadAll("configs", SimpleNamedConfig.class);

        assertEquals(List.of("one", "two"), configs.stream().map(config -> config.name).sorted().toList());
    }

    @Test
    void suppliedDefaultsAreDeepMergedWithExistingYaml() throws Exception {
        Files.writeString(tempDir.resolve("menu.yml"), """
                items:
                  previous:
                    label: "custom previous"
                  custom:
                    label: "custom item"
                    enabled: false
                """, StandardCharsets.UTF_8);

        DefaultsConfig config = manager().load("menu.yml", DefaultsConfig.class, DefaultsConfig::defaults);
        String yaml = Files.readString(tempDir.resolve("menu.yml"), StandardCharsets.UTF_8);

        assertEquals(List.of("previous", "next", "custom"), config.items.keySet().stream().toList());
        assertEquals("custom previous", config.items.get("previous").label);
        assertEquals(List.of(4), config.items.get("previous").slots);
        assertEquals("next", config.items.get("next").label);
        assertFalse(config.items.get("custom").enabled);
        assertTrue(yaml.contains("next:"));
        assertTrue(yaml.contains("custom item"));
    }

    @Test
    void suppliedDefaultsAreNotMutatedDuringLoad() throws Exception {
        DefaultsConfig defaults = DefaultsConfig.defaults();
        Files.writeString(tempDir.resolve("menu.yml"), """
                items:
                  previous:
                    label: "configured"
                """, StandardCharsets.UTF_8);

        DefaultsConfig loaded = manager().load("menu.yml", DefaultsConfig.class, () -> defaults);

        assertEquals("configured", loaded.items.get("previous").label);
        assertEquals("previous", defaults.items.get("previous").label);
    }

    @Test
    void suppliedOptionalEmptyValuesOverrideConstructorDefaults() {
        OptionalDefaultsConfig defaults = new OptionalDefaultsConfig();
        defaults.values = List.of();
        defaults.label = null;

        OptionalDefaultsConfig loaded = manager().load("optional-defaults.yml", OptionalDefaultsConfig.class,
                () -> defaults);

        assertEquals(List.of(), loaded.values);
        assertNull(loaded.label);
        assertEquals(List.of(), defaults.values);
        assertNull(defaults.label);
    }

    @Test
    void suppliedConvertedValuesAreCopied() {
        ConvertedDefaultsConfig defaults = new ConvertedDefaultsConfig();

        ConvertedDefaultsConfig loaded = manager().load("converted-defaults.yml", ConvertedDefaultsConfig.class,
                () -> defaults);
        loaded.value.text = "changed";

        assertNotSame(defaults.value, loaded.value);
        assertEquals("default", defaults.value.text);
        assertEquals("changed", loaded.value.text);
    }

    @Test
    void suppliedConvertedCollectionElementsUseTheirDeclaredType() {
        ConvertedCollectionDefaultsConfig defaults = new ConvertedCollectionDefaultsConfig();

        ConvertedCollectionDefaultsConfig loaded = manager().load(
                "converted-collection-defaults.yml",
                ConvertedCollectionDefaultsConfig.class,
                () -> defaults
        );
        MutableValue loadedValue = (MutableValue) loaded.values.getFirst();
        loadedValue.text = "changed";

        assertNotSame(defaults.values.getFirst(), loadedValue);
        assertEquals("default", defaults.values.getFirst().text());
        assertEquals("changed", loadedValue.text);
    }

    @Test
    void explicitEmptyOptionalCollectionSurvivesReload() throws Exception {
        Path path = tempDir.resolve("optional-empty.yml");
        Files.writeString(path, "values: []\n", StandardCharsets.UTF_8);

        OptionalDefaultsConfig first = manager().load("optional-empty.yml", OptionalDefaultsConfig.class);
        OptionalDefaultsConfig second = manager().load("optional-empty.yml", OptionalDefaultsConfig.class);

        assertEquals(List.of(), first.values);
        assertEquals(List.of(), second.values);
        assertTrue(Files.readString(path, StandardCharsets.UTF_8).contains("values: []"));
    }

    @Test
    void namedLoadingIsRecursiveAndDeterministic() throws Exception {
        Path dir = tempDir.resolve("gui/patterns");
        Files.createDirectories(dir.resolve("admin"));
        Files.writeString(dir.resolve("root.yml"), "name: \"root\"\n", StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("admin/pagination.yaml"), "name: \"pagination\"\n", StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("ignored.txt"), "name: \"ignored\"\n", StandardCharsets.UTF_8);

        List<NamedConfig<SimpleNamedConfig>> configs = manager()
                .loadAllNamed("gui/patterns", SimpleNamedConfig.class, true);

        assertEquals(List.of("admin/pagination", "root"), configs.stream().map(NamedConfig::id).toList());
        assertEquals(List.of("pagination", "root"), configs.stream().map(value -> value.value().name).toList());
        assertEquals(List.of("gui/patterns/admin/pagination.yaml", "gui/patterns/root.yml"),
                configs.stream().map(NamedConfig::relativePath).toList());
    }

    @Test
    void namedLoadingRejectsDuplicateExtensions() throws Exception {
        Path dir = tempDir.resolve("gui/menus");
        Files.createDirectories(dir);
        String original = "name: \"one\"\nunknown: \"preserved\"\n";
        Files.writeString(dir.resolve("same.yml"), original, StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("same.yaml"), "name: \"two\"\n", StandardCharsets.UTF_8);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> manager().loadAllNamed("gui/menus", SimpleNamedConfig.class, true));

        assertTrue(error.getMessage().contains("Duplicate config id"));
        assertEquals(original, Files.readString(dir.resolve("same.yml"), StandardCharsets.UTF_8));
    }

    @Test
    void nonMapYamlRootIsRejectedWithoutRewriting() throws Exception {
        Path file = tempDir.resolve("invalid-root.yml");
        String original = "- invalid\n- root\n";
        Files.writeString(file, original, StandardCharsets.UTF_8);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> manager().load("invalid-root.yml", SimpleNamedConfig.class));

        assertTrue(error.getMessage().contains("root must be a map"));
        assertEquals(original, Files.readString(file, StandardCharsets.UTF_8));
    }

    @Test
    void loadingRejectsPathsOutsideTheDataFolder() {
        assertThrows(IllegalArgumentException.class,
                () -> manager().load("../outside.yml", SimpleNamedConfig.class));
    }

    @Test
    void textFormattingCanKeepOrStripConfiguredColors() {
        Component template = ConfigManager.parseDynamic("&aHello {name}");

        Component colored = ConfigManager.fmt(template, Map.of("name", "&cNivcoo"), true);
        Component plain = ConfigManager.fmt(template, Map.of("name", "&cNivcoo"), false);

        assertEquals("Hello Nivcoo", PlainTextComponentSerializer.plainText().serialize(colored));
        assertEquals("Hello Nivcoo", PlainTextComponentSerializer.plainText().serialize(plain));
    }

    @Test
    void autoComponentsKeepExactHexColorsAcrossGeneratedYamlReloads() throws Exception {
        ConfigManager manager = manager();
        AutoHexConfig generated = manager.load("hex.yml", AutoHexConfig.class);
        AutoHexConfig reloaded = manager.load("hex.yml", AutoHexConfig.class);
        String yaml = Files.readString(tempDir.resolve("hex.yml"), StandardCharsets.UTF_8);

        assertEquals(TextColor.color(0x12ABEF), generated.label.color());
        assertEquals(TextColor.color(0x12ABEF), reloaded.label.color());
        assertTrue(yaml.toLowerCase(Locale.ROOT).contains("&#12abef"));
    }

    private ConfigManager manager() {
        return new ConfigManager(tempDir.toFile());
    }

    @Comment("Root header")
    public static final class ComplexConfig {
        @Comment("Server id")
        public String serverName = "lobby";
        @fr.nivcoo.utilsz.core.config.annotations.Path("routing.discovery.service")
        public String discoveryService = "edenplayers";
        @Section
        public Nested nested = new Nested();
        public List<Reward> rewards = List.of(new Reward("MONEY", 100));
        public Map<Integer, Reward> levels = Map.of(1, new Reward("XP", 25));
        public Map<String, List<Reward>> grouped = Map.of("vote", List.of(new Reward("KEY", 2)));
        public Duration timeout = Duration.ofSeconds(30);
        public Locale locale = Locale.FRANCE;
        public UUID uuid = UUID.fromString("00000000-0000-0000-0000-000000000000");
        @TextFormat(TextMode.LEGACY_AMP)
        public Component displayName = Component.text("Hello");
        @WithConverter(ConvertedValueConverter.class)
        public ConvertedValue converted = new ConvertedValue("default");
        @Optional
        @Section
        public OptionalSection optional = new OptionalSection();
    }

    public static final class Nested {
        public boolean enabled = true;
        public int intervalSeconds = 5;
    }

    public static final class OptionalSection {
        public String note = "";
    }

    public static final class Reward {
        public String type;
        public int amount;

        public Reward() {
        }

        public Reward(String type, int amount) {
            this.type = type;
            this.amount = amount;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof Reward other)) return false;
            return amount == other.amount && Objects.equals(type, other.type);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, amount);
        }
    }

    public record ConvertedValue(String value) {
    }

    public static final class ConvertedValueConverter implements Converter<ConvertedValue> {
        @Override
        public ConvertedValue read(Object raw, ConvertedValue fallback, Field field) {
            String value = String.valueOf(raw);
            if (value.startsWith("prefix:")) value = value.substring("prefix:".length());
            return new ConvertedValue(value);
        }

        @Override
        public Object write(ConvertedValue value, Field field) {
            return "prefix:" + value.value();
        }
    }

    public static final class RequiredConfig {
        @Required
        public String name = "";
    }

    public static final class RangeConfig {
        @Range(min = 1, max = 5)
        public int amount = 10;
    }

    @SaveOnLoad(false)
    public static final class ValidationGraphConfig implements Validatable {
        @Section
        public ValidationSection section = new ValidationSection();
        public ValidationValue direct = new ValidationValue();
        public List<ValidationValue> list = List.of(new ValidationValue());
        public Iterable<ValidationValue> iterable = List.of();
        public ValidationValue iterableValue = new ValidationValue();
        public Map<String, ValidationValue> map = Map.of("map", new ValidationValue());
        public ValidationValue[] array = {new ValidationValue()};
        public ValidationValue shared = new ValidationValue();
        private int validationCount;

        public ValidationGraphConfig() {
            iterable = List.of(iterableValue, shared);
            list = List.of(list.getFirst(), shared);
            map = Map.of("map", map.get("map"), "shared", shared);
            array = new ValidationValue[]{array[0], shared};
        }

        @Override
        public void validate() {
            validationCount++;
        }

        int validationCount() {
            return validationCount;
        }
    }

    public static final class ValidationSection implements Validatable {
        public boolean enabled = true;
        private int validationCount;

        @Override
        public void validate() {
            validationCount++;
        }

        int validationCount() {
            return validationCount;
        }
    }

    public static final class ValidationValue implements Validatable {
        @Section
        public ValidationSection section = new ValidationSection();
        private int validationCount;

        @Override
        public void validate() {
            validationCount++;
        }

        int validationCount() {
            return validationCount;
        }
    }

    @SaveOnLoad(false)
    public static final class InvalidListElementConfig {
        public List<RequiredElement> entries = List.of(new RequiredElement());
    }

    public static final class RequiredElement {
        @Required
        public String name = "";
    }

    @SaveOnLoad(false)
    public static final class InvalidMapElementConfig {
        public Map<String, RangeElement> entries = Map.of("premium", new RangeElement());
    }

    @SaveOnLoad(false)
    public static final class InvalidArrayElementConfig {
        public RangeElement[] entries = {new RangeElement()};
    }

    public static final class RangeElement {
        @Range(min = 1, max = 5)
        public int amount = 10;
    }

    public static final class InvalidValidatableConfig implements Validatable {
        public boolean valid = true;

        @Override
        public void validate() {
            if (!valid) throw new IllegalArgumentException("Configuration is invalid");
        }
    }

    @SaveOnLoad(false)
    public static final class ManualConfig {
        public String name = "default";
    }

    public static final class SimpleNamedConfig {
        public String name = "default";
    }

    public static final class DefaultsConfig {
        public Map<String, DefaultItem> items = new java.util.LinkedHashMap<>();

        static DefaultsConfig defaults() {
            DefaultsConfig config = new DefaultsConfig();
            config.items.put("previous", new DefaultItem("previous", true, List.of(4)));
            config.items.put("next", new DefaultItem("next", true, List.of(6)));
            return config;
        }
    }

    public static final class OptionalDefaultsConfig {
        @Optional
        public List<Integer> values = List.of(1);
        @Optional
        public String label = "constructor";
    }

    public static final class ConvertedDefaultsConfig {
        @Optional
        @WithConverter(MutableValueConverter.class)
        public MutableValue value = new MutableValue("default");
    }

    public static final class ConvertedCollectionDefaultsConfig {
        public List<MutableValueView> values = List.of(new MutableValue("default"));
    }

    @WithConverter(MutableValueViewConverter.class)
    public interface MutableValueView {
        String text();
    }

    public static final class MutableValue implements MutableValueView {
        private String text;

        public MutableValue(String text) {
            this.text = text;
        }

        @Override
        public String text() {
            return text;
        }
    }

    public static final class MutableValueConverter implements Converter<MutableValue> {
        @Override
        public MutableValue read(Object raw, MutableValue fallback, Field field) {
            return raw == null ? fallback : new MutableValue(String.valueOf(raw));
        }

        @Override
        public Object write(MutableValue value, Field field) {
            return value == null ? null : value.text;
        }
    }

    public static final class MutableValueViewConverter implements Converter<MutableValueView> {
        @Override
        public MutableValueView read(Object raw, MutableValueView fallback, Field field) {
            return raw == null ? fallback : new MutableValue(String.valueOf(raw));
        }

        @Override
        public Object write(MutableValueView value, Field field) {
            return value == null ? null : value.text();
        }
    }

    public static final class DefaultItem {
        public String label = "item";
        public boolean enabled = true;
        public List<Integer> slots = List.of();

        public DefaultItem() {
        }

        DefaultItem(String label, boolean enabled, List<Integer> slots) {
            this.label = label;
            this.enabled = enabled;
            this.slots = slots;
        }
    }

    public static final class AutoHexConfig {
        public Component label = Component.text("Exact", TextColor.color(0x12ABEF));
    }
}
