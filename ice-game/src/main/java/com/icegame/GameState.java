package com.icegame;

final class GameState {
    static final double WIDTH = 960, HEIGHT = 600;
    static final double PADDLE_R = 27, PUCK_R = 17;
    static final double PADDLE_X_MIN = 51;
    static final double MID_X = WIDTH / 2;
    static final double GOAL_TOP = 205, GOAL_BOTTOM = 395;
    static final double AIR_DRAG = 0.42;
    static final double MIN_SPEED = 55, MAX_SPEED = 820, MIN_BOUNCE = 130;

    double puckX, puckY, puckVx, puckVy;
    double leftX = PADDLE_X_MIN + 27, rightX = WIDTH - PADDLE_X_MIN - 27;
    double leftY = HEIGHT / 2, rightY = HEIGHT / 2;
    double leftVx, leftVy, rightVx, rightVy;
    int leftScore, rightScore;
    boolean finished;
    boolean lastHitWall;
    boolean lastHitPaddle;
    boolean scored;
    double lastHitSpeed;

    GameState() { resetPuck(1); }

    void resetAll() {
        leftScore = rightScore = 0;
        leftX = PADDLE_X_MIN + 27; rightX = WIDTH - PADDLE_X_MIN - 27;
        leftY = rightY = HEIGHT / 2;
        leftVx = leftVy = rightVx = rightVy = 0;
        finished = false;
        lastHitWall = lastHitPaddle = scored = false;
        resetPuck(Math.random() < .5 ? -1 : 1);
    }

    void resetPuck(int direction) {
        puckX = WIDTH / 2;
        puckY = HEIGHT / 2;
        puckVx = direction * 330;
        puckVy = (Math.random() * 220) - 110;
    }

    void update(double dt) {
        if (finished) return;
        puckX += puckVx * dt;
        puckY += puckVy * dt;
        double drag = Math.exp(-AIR_DRAG * dt);
        puckVx *= drag;
        puckVy *= drag;
        double spd = Math.hypot(puckVx, puckVy);
        if (spd < MIN_SPEED && spd > 0.01) {
            double k = MIN_SPEED / spd;
            puckVx *= k;
            puckVy *= k;
        }
        lastHitWall = lastHitPaddle = false;
        lastHitSpeed = 0;
        if (puckY - PUCK_R < 24) { puckY = 24 + PUCK_R; puckVy = Math.abs(puckVy) * .92; lastHitWall = true; }
        if (puckY + PUCK_R > HEIGHT - 24) { puckY = HEIGHT - 24 - PUCK_R; puckVy = -Math.abs(puckVy) * .92; lastHitWall = true; }
        boolean inGoalOpening = puckY > GOAL_TOP + PUCK_R && puckY < GOAL_BOTTOM - PUCK_R;
        if (!inGoalOpening && puckX - PUCK_R < 24) {
            puckX = 24 + PUCK_R;
            puckVx = Math.abs(puckVx) * .92;
            lastHitWall = true;
        }
        if (!inGoalOpening && puckX + PUCK_R > WIDTH - 24) {
            puckX = WIDTH - 24 - PUCK_R;
            puckVx = -Math.abs(puckVx) * .92;
            lastHitWall = true;
        }
        collide(leftX, leftY, leftVx, leftVy, true);
        collide(rightX, rightY, rightVx, rightVy, false);

        if (puckX < -PUCK_R) score(false);
        else if (puckX > WIDTH + PUCK_R) score(true);
    }

    private void collide(double x, double y, double pvx, double pvy, boolean left) {
        double dx = puckX - x, dy = puckY - y;
        double min = PADDLE_R + PUCK_R;
        double dist2 = dx * dx + dy * dy;
        if (dist2 >= min * min || dist2 == 0) return;
        double relVx = puckVx - pvx, relVy = puckVy - pvy;
        if (relVx * dx + relVy * dy >= 0) return;
        double dist = Math.sqrt(dist2), nx = dx / dist, ny = dy / dist;
        puckX = x + nx * min;
        puckY = y + ny * min;
        double incoming = Math.hypot(puckVx, puckVy);
        double edge = Math.abs(ny);
        double paddleSpd = Math.hypot(pvx, pvy);
        double speed = incoming * (0.78 + 0.34 * (1 - edge)) + paddleSpd * 0.45 + MIN_BOUNCE * (0.4 + 0.6 * edge);
        speed = Math.min(MAX_SPEED, speed);
        puckVx = nx * speed;
        puckVy = ny * speed + pvy * 0.35;
        if (left) puckVx = Math.abs(puckVx); else puckVx = -Math.abs(puckVx);
        lastHitPaddle = true;
        lastHitSpeed = speed;
    }

    private void score(boolean leftWonPoint) {
        if (leftWonPoint) leftScore++; else rightScore++;
        finished = leftScore >= 7 || rightScore >= 7;
        scored = true;
        if (!finished) resetPuck(leftWonPoint ? -1 : 1);
    }

    static double clampY(double value) {
        return Math.max(40, Math.min(HEIGHT - 40, value));
    }

    static double clampLeftX(double value) {
        return Math.max(PADDLE_X_MIN, Math.min(MID_X - PADDLE_R - 1, value));
    }

    static double clampRightX(double value) {
        return Math.max(MID_X + PADDLE_R + 1, Math.min(WIDTH - PADDLE_X_MIN, value));
    }
}
