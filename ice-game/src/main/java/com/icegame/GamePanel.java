package com.icegame;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.Locale;

final class GamePanel extends JPanel implements ActionListener {
    enum Mode { SINGLE, HOST, CLIENT }
    private static final Color BG = new Color(218, 244, 255);
    private static final Color BLUE = new Color(20, 112, 190);
    private static final Color RED = new Color(224, 55, 62);
    private static final Color PUCK = new Color(22, 30, 39);
    private static final Color RINK_FILL = new Color(239, 251, 255);
    private static final Color RINK_BORDER = new Color(39, 125, 174);
    private static final Color CENTER_LINE = new Color(223, 55, 64, 160);
    private static final Color SIDE_LINE = new Color(30, 108, 178, 120);
    private static final Color GOAL_LINE = new Color(225, 55, 65);
    private static final Color PADDLE_SHADOW = new Color(0, 0, 0, 35);
    private static final Color PADDLE_HL = new Color(255, 255, 255, 110);
    private static final Color STATUS = new Color(17, 53, 76);
    private static final Color OVERLAY = new Color(5, 25, 42, 210);

    private final GameFrame frame;
    private final Mode mode;
    private final GameState state = new GameState();
    private final Timer timer = new Timer(16, this);
    private final LanConnection network;
    private final AudioManager audio = AudioManager.get();
    private volatile double remoteY = GameState.HEIGHT / 2;
    private volatile double remoteX = -1;
    private double targetY = GameState.HEIGHT / 2;
    private double targetX = -1;
    private boolean up, down, left, right;
    private String status;
    private long lastNanos = System.nanoTime();
    private double aiAimDriftY, aiAimDriftX;
    private double aiReactionTimer;
    private double aiCommittedY, aiCommittedX;
    private double aiAggro;
    private boolean wasFinished;
    private boolean wasBgmOn;

    GamePanel(GameFrame frame, Mode mode, String host) {
        this.frame = frame;
        this.mode = mode;
        setPreferredSize(new Dimension((int) GameState.WIDTH, (int) GameState.HEIGHT));
        setFocusable(true);
        setBackground(BG);
        bindKeys();
        targetX = (mode == Mode.CLIENT) ? GameState.WIDTH - GameState.PADDLE_X_MIN - 27 : GameState.PADDLE_X_MIN + 27;

        if (mode == Mode.SINGLE) {
            network = null;
            status = "单人模式 · 你是蓝队";
            aiCommittedX = GameState.WIDTH - GameState.PADDLE_X_MIN - 27;
            aiCommittedY = GameState.HEIGHT / 2;
        } else {
            network = new LanConnection();
            if (mode == Mode.HOST) network.host(this::setStatus, this::readInput);
            else network.join(host, this::setStatus, this::readState);
        }
        timer.start();
    }

    private void bindKeys() {
        bind("pressed W", () -> up = true); bind("released W", () -> up = false);
        bind("pressed S", () -> down = true); bind("released S", () -> down = false);
        bind("pressed A", () -> left = true); bind("released A", () -> left = false);
        bind("pressed D", () -> right = true); bind("released D", () -> right = false);
        bind("pressed UP", () -> up = true); bind("released UP", () -> up = false);
        bind("pressed DOWN", () -> down = true); bind("released DOWN", () -> down = false);
        bind("pressed LEFT", () -> left = true); bind("released LEFT", () -> left = false);
        bind("pressed RIGHT", () -> right = true); bind("released RIGHT", () -> right = false);
        bind("pressed R", this::restart);
        bind("pressed ESCAPE", this::exitToMenu);
        bind("pressed M", this::toggleMute);
        bind("pressed B", this::toggleBgm);
    }

    private void toggleMute() {
        audio.setEnabled(!audio.isEnabled());
        setStatus(audio.isEnabled() ? "音效已开启" : "音效已关闭");
    }

    private void toggleBgm() {
        boolean on = !wasBgmOn;
        audio.setBgmEnabled(on);
        wasBgmOn = on;
        setStatus(on ? "背景音乐已开启" : "背景音乐已关闭");
    }

    private void bind(String stroke, Runnable action) {
        String key = stroke.replace(' ', '_');
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(stroke), key);
        getActionMap().put(key, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { action.run(); }
        });
    }

    @Override public void actionPerformed(ActionEvent e) {
        long now = System.nanoTime();
        double dt = Math.max(0.001, Math.min(.033, (now - lastNanos) / 1_000_000_000.0));
        lastNanos = now;
        moveLocal(dt);

        if (mode == Mode.SINGLE) {
            updateAi(dt);
            state.update(dt);
            consumeSoundFlags();
        } else if (mode == Mode.HOST && network.connected()) {
            double prevRX = state.rightX, prevRY = state.rightY;
            state.rightY = GameState.clampY(remoteY);
            state.rightX = GameState.clampRightX(remoteX < 0 ? state.rightX : remoteX);
            state.rightVx = (state.rightX - prevRX) / dt;
            state.rightVy = (state.rightY - prevRY) / dt;
            state.update(dt);
            consumeSoundFlags();
            network.send(String.format(Locale.ROOT, "S %.2f %.2f %.2f %.2f %.2f %.2f %.2f %.2f %d %d %b",
                    state.puckX, state.puckY, state.puckVx, state.puckVy,
                    state.leftX, state.leftY, state.rightX, state.rightY,
                    state.leftScore, state.rightScore, state.finished));
        } else if (mode == Mode.CLIENT && network.connected()) {
            network.send(String.format(Locale.ROOT, "I %.2f %.2f", targetX, targetY));
        }
        repaint();
    }

    private void consumeSoundFlags() {
        if (state.lastHitPaddle) {
            // 击球声：音量随碰撞速度动态缩放，音调随机浮动，重叠播放不截断
            audio.playHit(state.lastHitSpeed);
            state.lastHitPaddle = false;
        }
        if (state.lastHitWall) {
            audio.play(AudioManager.Sfx.BODY, 0.5, 1.0, false);
            state.lastHitWall = false;
        }
        if (state.scored) {
            audio.playGoal();
            state.scored = false;
            if (state.finished && !wasFinished) {
                boolean localWin = mode == Mode.CLIENT ? state.rightScore > state.leftScore : state.leftScore > state.rightScore;
                if (localWin) audio.play(AudioManager.Sfx.GOAL, 1.0, 1.0, true);
                wasFinished = true;
            }
        }
        if (!state.finished) wasFinished = false;
    }

    private void moveLocal(double dt) {
        double ky = (down ? 1 : 0) - (up ? 1 : 0);
        double kx = (right ? 1 : 0) - (left ? 1 : 0);
        if (ky != 0) targetY += ky * 560 * dt;
        if (kx != 0) targetX += kx * 560 * dt;
        targetY = GameState.clampY(targetY);
        if (mode == Mode.CLIENT) {
            targetX = GameState.clampRightX(targetX);
            state.rightX = approachX(state.rightX, targetX, 720 * dt, false);
            state.rightY = approach(state.rightY, targetY, 720 * dt);
        } else {
            targetX = GameState.clampLeftX(targetX);
            double prevX = state.leftX, prevY = state.leftY;
            state.leftX = approachX(state.leftX, targetX, 720 * dt, true);
            state.leftY = approach(state.leftY, targetY, 720 * dt);
            state.leftVx = (state.leftX - prevX) / dt;
            state.leftVy = (state.leftY - prevY) / dt;
        }
    }

    private void updateAi(double dt) {
        aiReactionTimer -= dt;
        double puckSpd = Math.hypot(state.puckVx, state.puckVy);
        boolean attacking = state.puckVx > 0 && state.puckX > GameState.WIDTH * .32;
        boolean deadBall = state.puckX > GameState.MID_X + 20 && puckSpd < 120;
        boolean redecide = aiReactionTimer <= 0;

        if (redecide) {
            double reactionDelay = deadBall ? .06 + Math.random() * .08 : .10 + Math.random() * .14;
            aiReactionTimer = reactionDelay;
            double errorScale = attacking ? 42 : 26;
            aiAimDriftY = (Math.random() - .5) * errorScale;
            aiAimDriftX = (Math.random() - .5) * errorScale * .6;
            if (deadBall) {
                aiAggro = Math.random() < .6 ? 1 : .5;
                aiCommittedX = GameState.clampRightX(state.puckX + 44 + aiAimDriftX);
                double aimOffset = (state.puckY < GameState.HEIGHT / 2 ? 1 : -1) * GameState.PADDLE_R * .8;
                aiCommittedY = GameState.clampY(state.puckY + aimOffset + aiAimDriftY);
            } else if (attacking) {
                aiAggro = Math.random() < .72 ? 1 : .35;
                double t = (state.puckX - state.rightX) / Math.max(1, state.puckVx);
                double interceptY = state.puckY + state.puckVy * Math.max(0, Math.min(.32, t));
                double aimOffset = (interceptY < GameState.HEIGHT / 2 ? 1 : -1) * GameState.PADDLE_R * .9;
                aiCommittedY = GameState.clampY(interceptY + aimOffset + aiAimDriftY);
                aiCommittedX = GameState.clampRightX(state.puckX + (aiAggro > .5 ? 44 : 78) + aiAimDriftX);
            } else if (state.puckX > GameState.MID_X && state.puckVx >= -10) {
                aiAggro = .4;
                aiCommittedX = GameState.clampRightX(state.puckX + 70 + aiAimDriftX);
                aiCommittedY = state.puckY + aiAimDriftY;
            } else {
                aiAggro = .2;
                aiCommittedX = GameState.WIDTH - GameState.PADDLE_X_MIN - 27;
                aiCommittedY = state.puckY * .4 + GameState.HEIGHT / 2 * .6 + aiAimDriftY * .5;
            }
        }

        double baseSpeed = 380 + aiAggro * 180;
        double jitter = 1 + (Math.random() - .5) * .12;
        double speed = baseSpeed * jitter;

        double prevX = state.rightX, prevY = state.rightY;
        state.rightX = approachX(state.rightX, aiCommittedX, speed * dt, false);
        state.rightY = approach(state.rightY, aiCommittedY, speed * dt);
        state.rightVx = (state.rightX - prevX) / dt;
        state.rightVy = (state.rightY - prevY) / dt;
    }

    private static double approach(double value, double target, double max) {
        return GameState.clampY(value + Math.max(-max, Math.min(max, target - value)));
    }

    private static double approachX(double value, double target, double max, boolean left) {
        double next = value + Math.max(-max, Math.min(max, target - value));
        return left ? GameState.clampLeftX(next) : GameState.clampRightX(next);
    }

    private void readInput(String line) {
        try {
            if (!line.startsWith("I ")) return;
            String[] p = line.split(" ");
            if (p.length == 3) {
                remoteX = Double.parseDouble(p[1]);
                remoteY = Double.parseDouble(p[2]);
            } else if (p.length == 2) {
                remoteY = Double.parseDouble(p[1]);
            }
        } catch (RuntimeException ignored) {}
    }

    private void readState(String line) {
        if (!line.startsWith("S ")) return;
        String[] p = line.split(" ");
        if (p.length != 12) return;
        try {
            synchronized (state) {
                int prevL = state.leftScore, prevR = state.rightScore;
                boolean prevFinished = state.finished;
                state.puckX = Double.parseDouble(p[1]); state.puckY = Double.parseDouble(p[2]);
                state.puckVx = Double.parseDouble(p[3]); state.puckVy = Double.parseDouble(p[4]);
                state.leftX = Double.parseDouble(p[5]);
                state.leftY = Double.parseDouble(p[6]);
                state.leftScore = Integer.parseInt(p[9]); state.rightScore = Integer.parseInt(p[10]);
                state.finished = Boolean.parseBoolean(p[11]);
                if (state.leftScore != prevL || state.rightScore != prevR) {
                    audio.playGoal();
                    if (state.finished && !prevFinished) {
                        boolean localWin = mode == Mode.CLIENT ? state.rightScore > state.leftScore : state.leftScore > state.rightScore;
                        if (localWin) audio.play(AudioManager.Sfx.GOAL, 1.0, 1.0, true);
                    }
                }
            }
        } catch (RuntimeException ignored) {}
    }

    private void restart() {
        if (mode != Mode.CLIENT) state.resetAll();
    }

    private void exitToMenu() {
        timer.stop();
        if (network != null) network.close();
        frame.showMenu();
    }

    private void setStatus(String value) { status = value; }

    @Override protected void paintComponent(Graphics raw) {
        super.paintComponent(raw);
        Graphics2D g = (Graphics2D) raw.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        paintRink(g);
        synchronized (state) {
            paddle(g, state.leftX, state.leftY, BLUE);
            paddle(g, state.rightX, state.rightY, RED);
            g.setColor(PUCK);
            g.fill(new Ellipse2D.Double(state.puckX - GameState.PUCK_R, state.puckY - GameState.PUCK_R,
                    GameState.PUCK_R * 2, GameState.PUCK_R * 2));
            paintScore(g);
        }
        g.dispose();
    }

    private void paintRink(Graphics2D g) {
        g.setColor(RINK_FILL);
        g.fillRoundRect(18, 18, 924, 564, 95, 95);
        g.setStroke(new BasicStroke(5));
        g.setColor(RINK_BORDER);
        g.drawRoundRect(20, 20, 920, 560, 90, 90);
        g.setStroke(new BasicStroke(3));
        g.setColor(CENTER_LINE);
        g.drawLine(480, 25, 480, 575);
        g.draw(new Ellipse2D.Double(395, 215, 170, 170));
        g.setColor(SIDE_LINE);
        g.drawLine(255, 25, 255, 575);
        g.drawLine(705, 25, 705, 575);
        g.setStroke(new BasicStroke(9));
        g.setColor(GOAL_LINE);
        g.drawLine(21, (int) GameState.GOAL_TOP, 21, (int) GameState.GOAL_BOTTOM);
        g.drawLine(939, (int) GameState.GOAL_TOP, 939, (int) GameState.GOAL_BOTTOM);
    }

    private void paddle(Graphics2D g, double x, double y, Color color) {
        g.setColor(PADDLE_SHADOW);
        g.fill(new Ellipse2D.Double(x - 31, y - 24, 62, 56));
        g.setColor(color);
        g.fill(new Ellipse2D.Double(x - 27, y - 27, 54, 54));
        g.setColor(PADDLE_HL);
        g.fill(new Ellipse2D.Double(x - 14, y - 16, 21, 13));
    }

    private void paintScore(Graphics2D g) {
        g.setFont(new Font("SansSerif", Font.BOLD, 44));
        g.setColor(BLUE);
        centered(g, Integer.toString(state.leftScore), 420, 68);
        g.setColor(RED);
        centered(g, Integer.toString(state.rightScore), 540, 68);
        g.setFont(new Font("SansSerif", Font.BOLD, 16));
        g.setColor(STATUS);
        centered(g, status == null ? "准备中…" : status, 480, 565);
        if (state.finished) {
            g.setColor(OVERLAY);
            g.fillRoundRect(250, 205, 460, 190, 28, 28);
            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, 38));
            boolean localWin = mode == Mode.CLIENT ? state.rightScore > state.leftScore : state.leftScore > state.rightScore;
            centered(g, localWin ? "你赢了！" : "对手获胜", 480, 278);
            g.setFont(new Font("SansSerif", Font.PLAIN, 18));
            centered(g, "按 R 再来一局 · Esc 返回菜单", 480, 337);
        }
    }

    private static void centered(Graphics2D g, String text, int x, int y) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, x - fm.stringWidth(text) / 2, y);
    }
}
