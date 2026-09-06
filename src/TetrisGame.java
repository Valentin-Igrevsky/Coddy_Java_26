import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * Управление:
 *   ← / →   - двигать фигуру влево/вправо
 *   ↓       - ускорить падение (мягкий сброс)
 *   ↑       - повернуть фигуру
 *   ПРОБЕЛ  - мгновенно уронить фигуру вниз (жёсткий сброс)
 *   ENTER   - начать заново после проигрыша
 */

public class TetrisGame extends JPanel implements ActionListener, KeyListener {

    // =====================================================================
    //  БЛОК НАСТРОЕК — тут можно менять размер поля, цвета и скорость
    // =====================================================================

    static final int COLS = 10;              // ширина поля в клетках
    static final int ROWS = 20;               // высота поля в клетках
    static final int CELL_SIZE = 30;          // размер одной клетки в пикселях
    static final int PREVIEW_PANEL_WIDTH = 150; // ширина панели "следующая фигура"

    // Цвет фона игрового поля                                    // >>> ИЗМЕНИ МЕНЯ <<<
    static final Color BACKGROUND_COLOR = new Color(20, 20, 30);
    // Цвет линий сетки
    static final Color GRID_COLOR = new Color(60, 60, 70);
    // Цвет панели справа (счёт, следующая фигура)
    static final Color SIDE_PANEL_COLOR = new Color(35, 35, 45);

    // ---- Цвета фигур (тетромино) — каждый можно менять отдельно ----     // >>> ИЗМЕНИ МЕНЯ <<<
    static final Color COLOR_I = new Color(255, 255, 255);   // голубая палка
    static final Color COLOR_O = new Color(255, 255, 255);   // жёлтый квадрат
    static final Color COLOR_T = new Color(255, 255, 255);   // фиолетовая буква T
    static final Color COLOR_S = new Color(255, 255, 255);     // зелёная S
    static final Color COLOR_Z = new Color(255, 255, 255);     // красная Z
    static final Color COLOR_J = new Color(255, 255, 255);     // синяя J
    static final Color COLOR_L = new Color(255, 255, 255);   // оранжевая L

    static final Color[] PIECE_COLORS = {
            COLOR_I, COLOR_O, COLOR_T, COLOR_S, COLOR_Z, COLOR_J, COLOR_L
    };

    // ---- Скорость падения фигур ----
    // Значение от 1 (медленно, легко) до 5 (быстро, сложно)          // >>> ИЗМЕНИ МЕНЯ <<<
    static final int FALL_SPEED = 3;

    // Таблица: какому значению FALL_SPEED соответствует какая задержка (в миллисекундах).
    static final int[] SPEED_TO_DELAY_MS = {
            1000, // FALL_SPEED = 1 — самая медленная скорость
            800,  // FALL_SPEED = 2
            600,  // FALL_SPEED = 3
            400,  // FALL_SPEED = 4
            200   // FALL_SPEED = 5 — самая быстрая скорость
    };

    // Очки за одновременную очистку 1, 2, 3 или 4 линий
    static final int[] SCORE_FOR_LINES = { 0, 100, 300, 500, 800 };

    // =====================================================================
    //  ФИГУРЫ (ТЕТРОМИНО) — координаты клеток для каждой фигуры и поворота
    // =====================================================================

    static final int[][][][] SHAPES = {
            // 0: I
            {
                    {{0,1},{1,1},{2,1},{3,1}},
                    {{2,0},{2,1},{2,2},{2,3}},
                    {{0,2},{1,2},{2,2},{3,2}},
                    {{1,0},{1,1},{1,2},{1,3}}
            },
            // 1: O
            {
                    {{1,0},{2,0},{1,1},{2,1}},
                    {{1,0},{2,0},{1,1},{2,1}},
                    {{1,0},{2,0},{1,1},{2,1}},
                    {{1,0},{2,0},{1,1},{2,1}}
            },
            // 2: T
            {
                    {{1,0},{0,1},{1,1},{2,1}},
                    {{1,0},{1,1},{2,1},{1,2}},
                    {{0,1},{1,1},{2,1},{1,2}},
                    {{1,0},{0,1},{1,1},{1,2}}
            },
            // 3: S
            {
                    {{1,0},{2,0},{0,1},{1,1}},
                    {{1,0},{1,1},{2,1},{2,2}},
                    {{1,0},{2,0},{0,1},{1,1}},
                    {{1,0},{1,1},{2,1},{2,2}}
            },
            // 4: Z
            {
                    {{0,0},{1,0},{1,1},{2,1}},
                    {{2,0},{1,1},{2,1},{1,2}},
                    {{0,0},{1,0},{1,1},{2,1}},
                    {{2,0},{1,1},{2,1},{1,2}}
            },
            // 5: J
            {
                    {{0,0},{0,1},{1,1},{2,1}},
                    {{1,0},{2,0},{1,1},{1,2}},
                    {{0,1},{1,1},{2,1},{2,2}},
                    {{1,0},{1,1},{0,2},{1,2}}
            },
            // 6: L
            {
                    {{2,0},{0,1},{1,1},{2,1}},
                    {{1,0},{1,1},{1,2},{2,2}},
                    {{0,1},{1,1},{2,1},{0,2}},
                    {{0,0},{1,0},{1,1},{1,2}}
            }
    };

    // =====================================================================
    //  СОСТОЯНИЕ ИГРЫ
    // =====================================================================

    private final int[][] board = new int[ROWS][COLS];

    private int currentType;
    private int currentRotation;
    private int currentX;
    private int currentY;

    private int nextType;

    private int score = 0;
    private int linesCleared = 0;
    private boolean gameOver = false;

    private final Timer timer;

    public TetrisGame() {
        int panelWidth = COLS * CELL_SIZE + PREVIEW_PANEL_WIDTH;
        int panelHeight = ROWS * CELL_SIZE;
        setPreferredSize(new Dimension(panelWidth, panelHeight));
        setFocusable(true);
        addKeyListener(this);

        for (int[] row : board) {
            java.util.Arrays.fill(row, -1);
        }

        nextType = randomType();
        spawnNewPiece();

        int delay = SPEED_TO_DELAY_MS[clamp(FALL_SPEED, 1, 5) - 1];
        timer = new Timer(delay, this);
        timer.start();
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private int randomType() {
        return (int) (Math.random() * SHAPES.length);
    }

    // Создаёт новую фигуру наверху поля. Если места нет — игра окончена.
    private void spawnNewPiece() {
        currentType = nextType;
        nextType = randomType();
        currentRotation = 0;
        currentX = COLS / 2 - 2;
        currentY = 0;

        if (!canPlace(currentType, currentRotation, currentX, currentY)) {
            gameOver = true;
            timer.stop();
        }
    }

    // Проверяет, можно ли разместить фигуру в данной позиции и повороте
    private boolean canPlace(int type, int rotation, int px, int py) {
        for (int[] cell : SHAPES[type][rotation]) {
            int x = px + cell[0];
            int y = py + cell[1];

            if (x < 0 || x >= COLS || y >= ROWS) {
                return false; // вышли за границы поля
            }
            if (y >= 0 && board[y][x] != -1) {
                return false; // клетка уже занята
            }
        }
        return true;
    }

    // "Впечатывает" текущую фигуру в поле (когда она больше не может падать)
    private void lockPiece() {
        for (int[] cell : SHAPES[currentType][currentRotation]) {
            int x = currentX + cell[0];
            int y = currentY + cell[1];
            if (y >= 0 && y < ROWS && x >= 0 && x < COLS) {
                board[y][x] = currentType;
            }
        }
        clearFullLines();
        spawnNewPiece();
    }

    // Убирает заполненные линии и двигает всё, что выше, вниз
    private void clearFullLines() {
        int cleared = 0;

        for (int y = ROWS - 1; y >= 0; y--) {
            boolean full = true;
            for (int x = 0; x < COLS; x++) {
                if (board[y][x] == -1) {
                    full = false;
                    break;
                }
            }

            if (full) {
                cleared++;
                // Сдвигаем все строки выше на одну строку вниз
                for (int row = y; row > 0; row--) {
                    board[row] = board[row - 1].clone();
                }
                java.util.Arrays.fill(board[0], -1);
                y++; // проверяем эту же строку ещё раз, т.к. туда сдвинулась новая
            }
        }

        if (cleared > 0) {
            linesCleared += cleared;
            score += SCORE_FOR_LINES[Math.min(cleared, 4)];
        }
    }

    // Пытается сдвинуть текущую фигуру. Возвращает true, если получилось.
    private boolean tryMove(int dx, int dy) {
        int newX = currentX + dx;
        int newY = currentY + dy;
        if (canPlace(currentType, currentRotation, newX, newY)) {
            currentX = newX;
            currentY = newY;
            return true;
        }
        return false;
    }

    // Пытается повернуть текущую фигуру (с небольшой попыткой сдвига, если не влезает)
    private void tryRotate() {
        int newRotation = (currentRotation + 1) % 4;

        int[] kicks = {0, -1, 1, -2, 2}; // варианты сдвига по X, если поворот не влезает
        for (int kick : kicks) {
            if (canPlace(currentType, newRotation, currentX + kick, currentY)) {
                currentRotation = newRotation;
                currentX = currentX + kick;
                return;
            }
        }
        // если ни один вариант не подошёл — поворот не выполняется
    }

    // Мгновенно роняет фигуру до упора вниз
    private void hardDrop() {
        while (tryMove(0, 1)) {
            // двигаем вниз, пока можем
        }
        lockPiece();
        repaint();
    }

    private void restartGame() {
        for (int[] row : board) {
            java.util.Arrays.fill(row, -1);
        }
        score = 0;
        linesCleared = 0;
        gameOver = false;
        nextType = randomType();
        spawnNewPiece();
        timer.start();
    }

    // Этот метод вызывается таймером на каждый "тик" — здесь падает фигура
    @Override
    public void actionPerformed(ActionEvent e) {
        if (gameOver) return;

        if (!tryMove(0, 1)) {
            lockPiece();
        }
        repaint();
    }

    // =====================================================================
    //  ОТРИСОВКА
    // =====================================================================

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        // Игровое поле
        g.setColor(BACKGROUND_COLOR);
        g.fillRect(0, 0, COLS * CELL_SIZE, ROWS * CELL_SIZE);

        drawGrid(g);
        drawBoard(g);
        drawCurrentPiece(g);
        drawSidePanel(g);

        if (gameOver) {
            drawGameOver(g);
        }
    }

    private void drawGrid(Graphics g) {
        g.setColor(GRID_COLOR);
        for (int x = 0; x <= COLS; x++) {
            g.drawLine(x * CELL_SIZE, 0, x * CELL_SIZE, ROWS * CELL_SIZE);
        }
        for (int y = 0; y <= ROWS; y++) {
            g.drawLine(0, y * CELL_SIZE, COLS * CELL_SIZE, y * CELL_SIZE);
        }
    }

    private void drawBoard(Graphics g) {
        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLS; x++) {
                int type = board[y][x];
                if (type != -1) {
                    drawCell(g, x, y, PIECE_COLORS[type]);
                }
            }
        }
    }

    private void drawCurrentPiece(Graphics g) {
        if (gameOver) return;
        for (int[] cell : SHAPES[currentType][currentRotation]) {
            int x = currentX + cell[0];
            int y = currentY + cell[1];
            if (y >= 0) {
                drawCell(g, x, y, PIECE_COLORS[currentType]);
            }
        }
    }

    private void drawCell(Graphics g, int col, int row, Color color) {
        int px = col * CELL_SIZE;
        int py = row * CELL_SIZE;
        g.setColor(color);
        g.fillRect(px + 1, py + 1, CELL_SIZE - 2, CELL_SIZE - 2);
        g.setColor(color.darker());
        g.drawRect(px + 1, py + 1, CELL_SIZE - 2, CELL_SIZE - 2);
    }

    private void drawSidePanel(Graphics g) {
        int panelX = COLS * CELL_SIZE;
        int panelHeight = ROWS * CELL_SIZE;

        g.setColor(SIDE_PANEL_COLOR);
        g.fillRect(panelX, 0, PREVIEW_PANEL_WIDTH, panelHeight);

        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 16));
        g.drawString("Счёт: " + score, panelX + 15, 30);
        g.drawString("Линии: " + linesCleared, panelX + 15, 55);

        g.drawString("Дальше:", panelX + 15, 100);
        drawNextPiecePreview(g, panelX + 15, 115);

        g.setFont(new Font("Arial", Font.PLAIN, 12));
        g.drawString("← → двигать", panelX + 15, panelHeight - 90);
        g.drawString("↑ повернуть", panelX + 15, panelHeight - 70);
        g.drawString("↓ ускорить", panelX + 15, panelHeight - 50);
        g.drawString("ПРОБЕЛ уронить", panelX + 15, panelHeight - 30);
    }

    private void drawNextPiecePreview(Graphics g, int startX, int startY) {
        int previewCell = 20;
        for (int[] cell : SHAPES[nextType][0]) {
            int px = startX + cell[0] * previewCell;
            int py = startY + cell[1] * previewCell;
            g.setColor(PIECE_COLORS[nextType]);
            g.fillRect(px, py, previewCell - 2, previewCell - 2);
        }
    }

    private void drawGameOver(Graphics g) {
        g.setColor(new Color(0, 0, 0, 180));
        g.fillRect(0, 0, COLS * CELL_SIZE, ROWS * CELL_SIZE);

        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 26));
        g.drawString("ИГРА ОКОНЧЕНА", 20, ROWS * CELL_SIZE / 2 - 20);

        g.setFont(new Font("Arial", Font.PLAIN, 16));
        g.drawString("Счёт: " + score, 20, ROWS * CELL_SIZE / 2 + 10);
        g.drawString("Нажми ENTER для новой игры", 20, ROWS * CELL_SIZE / 2 + 35);
    }

    // =====================================================================
    //  УПРАВЛЕНИЕ С КЛАВИАТУРЫ
    // =====================================================================

    @Override
    public void keyPressed(KeyEvent e) {
        if (gameOver) {
            if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                restartGame();
                repaint();
            }
            return;
        }

        switch (e.getKeyCode()) {
            case KeyEvent.VK_LEFT:
                tryMove(-1, 0);
                break;
            case KeyEvent.VK_RIGHT:
                tryMove(1, 0);
                break;
            case KeyEvent.VK_DOWN:
                if (!tryMove(0, 1)) {
                    lockPiece();
                }
                break;
            case KeyEvent.VK_UP:
                tryRotate();
                break;
            case KeyEvent.VK_SPACE:
                hardDrop();
                break;
        }
        repaint();
    }

    @Override
    public void keyReleased(KeyEvent e) {}

    @Override
    public void keyTyped(KeyEvent e) {}

    public static void main(String[] args) {
        JFrame frame = new JFrame("Тетрис");
        TetrisGame game = new TetrisGame();

        frame.add(game);
        frame.pack();
        frame.setLocationRelativeTo(null); // окно по центру экрана
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(false);
        frame.setVisible(true);
    }
}