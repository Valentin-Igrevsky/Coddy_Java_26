import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Генерирует комбинированный тест из 4 тем (занятий), по 3 вопроса на тему -
 * лёгкий, средний, сложный. Вопрос каждого уровня выбирается случайно из
 * банка (TestsRedactorData/question-bank), где на каждую тему и сложность
 * заготовлено по 5 вопросов.
 * <p>
 * Рассчитан на запуск без участия человека (например, в GitHub Actions по
 * кнопке "Run workflow"), поэтому не использует Scanner и не ждёт ввода.
 * Также нормально работает и при локальном запуске.
 */
public class ComboTestGenerator {

    private static final String DATA_DIR = "TestsRedactorData";
    private static final String BANK_DIR = DATA_DIR + "/question-bank";
    private static final String TEMPLATE_FILE = DATA_DIR + "/template.html";

    private static final String CONTROL_WORK_DIR = "ControlWork";
    private static final String OUTPUT_FILE_NAME = "Test-Combo.html";

    private static final String README_FILE = "README.md";
    private static final String README_SECTION_MARKER = "## Контрольные работы и тесты";
    private static final String GITHUB_PAGES_BASE = "https://valentin-igrevsky.github.io/Coddy_Java_26/";

    private static final String[] DIFFICULTIES = {"easy", "medium", "hard"};
    private static final String[] DIFFICULTY_LABELS = {"Лёгкий", "Средний", "Сложный"};

    private static final Theme[] THEMES = {
            new Theme("theme1", "Занятие 1: Java — основы и переменные"),
            new Theme("theme2", "Занятие 2: Операции и типы данных"),
            new Theme("theme3", "Занятие 3: Условные операторы"),
            new Theme("theme4", "Занятие 4: Циклы")
    };

    private final Random random = new Random();

    public static void main(String[] args) {
        try {
            new ComboTestGenerator().run();
        } catch (IOException e) {
            System.err.println("Ошибка при генерации теста: " + e.getMessage());
            System.exit(1);
        }
    }

    public void run() throws IOException {
        List<String> selectedQuestions = new ArrayList<>();

        for (Theme theme : THEMES) {
            for (int i = 0; i < DIFFICULTIES.length; i++) {
                String difficulty = DIFFICULTIES[i];
                String difficultyLabel = DIFFICULTY_LABELS[i];

                String fileContent = readFile(BANK_DIR + "/" + theme.id + "-" + difficulty + ".json");
                List<String> pool = splitJsonArrayElements(extractArrayContent(fileContent));

                if (pool.isEmpty()) {
                    throw new IOException("Пустой банк вопросов: " + theme.id + "-" + difficulty + ".json");
                }

                String picked = pool.get(random.nextInt(pool.size()));
                selectedQuestions.add(withMeta(picked, theme.title, difficultyLabel));

                System.out.println(theme.title + " [" + difficultyLabel + "] — вопрос выбран");
            }
        }

        String questionsJson = "[\n  " + String.join(",\n  ", selectedQuestions) + "\n]";

        String template = readFile(TEMPLATE_FILE);
        String html = template
                .replace("{{TEST_NUMBER}}", "1\u20134")
                .replace("{{QUESTIONS_JSON}}", questionsJson);

        Path outputPath = saveTest(html);
        System.out.println("✅ Тест сохранён: " + outputPath);

        boolean readmeUpdated = updateReadme();
        System.out.println(readmeUpdated
                ? "✅ README.md обновлён."
                : "ℹ️ Ссылка на итоговый тест в README.md уже есть — пропускаю.");
    }

    /**
     * Вставляет метаданные темы и сложности в начало JSON-объекта вопроса,
     * чтобы шаблон мог показать тег "Занятие N · Сложность" под вопросом.
     */
    private String withMeta(String rawObject, String themeTitle, String difficultyLabel) {
        String trimmed = rawObject.trim();
        if (!trimmed.startsWith("{")) {
            throw new IllegalStateException("Ожидался JSON-объект, получено: " + trimmed);
        }
        String body = trimmed.substring(1); // убираем первую "{"
        String meta = "\"theme\": " + jsonString(themeTitle) + ", \"difficulty\": " + jsonString(difficultyLabel) + ", ";
        return "{" + meta + body;
    }

    private String jsonString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private String readFile(String path) throws IOException {
        Path filePath = Path.of(path);
        if (!Files.exists(filePath)) {
            throw new IOException("Файл не найден: " + filePath.toAbsolutePath());
        }
        return Files.readString(filePath, StandardCharsets.UTF_8);
    }

    private String extractArrayContent(String json) throws IOException {
        int start = json.indexOf('[');
        int end = json.lastIndexOf(']');
        if (start == -1 || end == -1 || end < start) {
            throw new IOException("Файл не похож на JSON-массив вопросов");
        }
        return json.substring(start + 1, end);
    }

    /**
     * Разбивает содержимое JSON-массива на отдельные объекты верхнего уровня.
     * Учитывает вложенные { } и [ ], а также пропускает скобки внутри строк
     * (например, фигурные скобки Java-кода внутри поля "code").
     */
    private List<String> splitJsonArrayElements(String arrayContent) {
        List<String> elements = new ArrayList<>();
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        StringBuilder current = new StringBuilder();

        for (char c : arrayContent.toCharArray()) {
            if (inString) {
                current.append(c);
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }

            if (c == '"') {
                inString = true;
                current.append(c);
                continue;
            }

            if (c == '{' || c == '[') {
                depth++;
                current.append(c);
                continue;
            }

            if (c == '}' || c == ']') {
                depth--;
                current.append(c);
                continue;
            }

            if (c == ',' && depth == 0) {
                addIfNotEmpty(elements, current);
                continue;
            }

            current.append(c);
        }
        addIfNotEmpty(elements, current);

        return elements;
    }

    private void addIfNotEmpty(List<String> elements, StringBuilder current) {
        String trimmed = current.toString().trim();
        if (!trimmed.isEmpty()) {
            elements.add(trimmed);
        }
        current.setLength(0);
    }

    private Path saveTest(String html) throws IOException {
        Path dir = Path.of(CONTROL_WORK_DIR);
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
        Path outputFile = dir.resolve(OUTPUT_FILE_NAME);
        Files.writeString(outputFile, html, StandardCharsets.UTF_8);
        return outputFile;
    }

    private boolean updateReadme() throws IOException {
        Path readmePath = Path.of(README_FILE);
        String content = readFile(readmePath.toString());

        String link = GITHUB_PAGES_BASE + "ControlWork/" + OUTPUT_FILE_NAME;
        if (content.contains(link)) {
            return false;
        }

        String newLine = "- [**Итоговый тест по занятиям 1–4** (генерируется случайно)](" + link + ")";

        int markerIndex = content.indexOf(README_SECTION_MARKER);
        if (markerIndex == -1) {
            throw new IOException("В README.md не найден раздел \"" + README_SECTION_MARKER + "\"");
        }

        int separatorIndex = content.indexOf("\n---", markerIndex);
        int insertPos = separatorIndex == -1 ? content.length() : separatorIndex;

        String before = content.substring(0, insertPos);
        String after = content.substring(insertPos);

        String updated = before.stripTrailing() + "\n" + newLine + "\n" + after;
        Files.writeString(readmePath, updated, StandardCharsets.UTF_8);
        return true;
    }

    private static class Theme {
        final String id;
        final String title;

        Theme(String id, String title) {
            this.id = id;
            this.title = title;
        }
    }
}
