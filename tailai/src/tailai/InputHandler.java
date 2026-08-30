package tailai;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.HashSet;
import java.util.Set;

/**
 * 键盘/鼠标输入。Swing 事件在 EDT 线程写入，游戏循环线程每帧调用 snapshot() 取一份一致快照。
 * 按下边沿（pressed）与滚轮在取快照后清空，供"只触发一次"的逻辑使用。
 */
public class InputHandler implements KeyListener, MouseListener, MouseMotionListener, MouseWheelListener {

    private final Object lock = new Object();
    private final Set<Integer> keys = new HashSet<>();
    private final Set<Integer> pressed = new HashSet<>();
    private boolean mouseL, mouseR, mouseLP, mouseRP;
    private int mouseX, mouseY;
    private int scroll;
    private final StringBuilder typedBuf = new StringBuilder();

    @Override
    public void keyPressed(KeyEvent e) {
        synchronized (lock) {
            if (!keys.contains(e.getKeyCode())) {
                pressed.add(e.getKeyCode());
            }
            keys.add(e.getKeyCode());
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        synchronized (lock) {
            keys.remove(e.getKeyCode());
        }
    }

    @Override
    public void keyTyped(KeyEvent e) {
        synchronized (lock) {
            char c = e.getKeyChar();
            if (c >= 32 && c != 127) {
                typedBuf.append(c);
            }
        }
    }

    @Override
    public void mouseClicked(MouseEvent e) {
    }

    @Override
    public void mousePressed(MouseEvent e) {
        synchronized (lock) {
            if (e.getButton() == MouseEvent.BUTTON1) {
                mouseL = true;
                mouseLP = true;
            } else if (e.getButton() == MouseEvent.BUTTON3) {
                mouseR = true;
                mouseRP = true;
            }
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        synchronized (lock) {
            if (e.getButton() == MouseEvent.BUTTON1) {
                mouseL = false;
            } else if (e.getButton() == MouseEvent.BUTTON3) {
                mouseR = false;
            }
        }
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        synchronized (lock) {
            mouseX = e.getX();
            mouseY = e.getY();
        }
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        synchronized (lock) {
            mouseX = e.getX();
            mouseY = e.getY();
        }
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        synchronized (lock) {
            scroll += e.getWheelRotation();
        }
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {
    }

    /** 取一帧快照；同时清空边沿事件与滚轮累积。 */
    public Snapshot snapshot() {
        synchronized (lock) {
            Snapshot s = new Snapshot();
            s.keys.addAll(keys);
            s.pressed.addAll(pressed);
            s.mouseLeft = mouseL;
            s.mouseRight = mouseR;
            s.mouseLeftPressed = mouseLP;
            s.mouseRightPressed = mouseRP;
            s.mouseX = mouseX;
            s.mouseY = mouseY;
            s.scroll = scroll;
            s.typed = typedBuf.toString();
            typedBuf.setLength(0);
            pressed.clear();
            mouseLP = false;
            mouseRP = false;
            scroll = 0;
            return s;
        }
    }

    /** 一帧内的输入快照。 */
    public static class Snapshot {
        public final Set<Integer> keys = new HashSet<>();
        public final Set<Integer> pressed = new HashSet<>();
        public boolean mouseLeft, mouseRight, mouseLeftPressed, mouseRightPressed;
        public int mouseX, mouseY, scroll;
        public String typed = "";

        public boolean key(int code) {
            return keys.contains(code);
        }

        public boolean pressed(int code) {
            return pressed.contains(code);
        }
    }
}
