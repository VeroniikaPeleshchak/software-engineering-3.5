import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;


public class MultiThreadDemo extends JFrame {

    private interface ControlledWorker {
        void requestStart();
        void requestStop();
        void setPaused(boolean paused);
        void setDelay(int delayMs);
        void setPriority(int prio);
        boolean isRunning();
        boolean isPaused();
    }

    //Панель анімації
    private static class AnimationPanel extends JPanel {

        private double time = 0;

        // Частинки
        private static class Particle {
            double x, y;
            double vx, vy;
            float alpha;
        }

        private final java.util.List<Particle> particles = new java.util.ArrayList<>();
        private final int MAX_PARTICLES = 200;

        public AnimationPanel() {
            setDoubleBuffered(true);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            Graphics2D g2 = (Graphics2D) g.create();
            int w = getWidth();
            int h = getHeight();

            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            GradientPaint gp = new GradientPaint(
                    0, 0, new Color(10, 10, 25),
                    0, h, new Color(0, 40, 80)
            );
            g2.setPaint(gp);
            g2.fillRect(0, 0, w, h);

            //Фрактальні хвилі
            for (int x = 0; x < w; x++) {

                double fx = x / 40.0;

                double noise =
                        Math.sin(fx + time * 1.5) * 20 +
                                Math.sin(fx * 1.3 + time * 0.9) * 15 +
                                Math.sin(fx * 0.7 + time * 2.5) * 10;

                int y = (int)(h / 2 + noise);

                g2.setColor(new Color(120, 160, 255, 160));
                g2.fillOval(x, y, 2, 2);
            }

            spawnParticles(w, h);
            updateParticles(w, h);

            for (Particle p : particles) {
                int size = 6;

                g2.setColor(new Color(255, 200, 150, (int)(p.alpha * 255)));
                g2.fillOval((int)p.x, (int)p.y, size, size);
            }

            //Світлова фігура
            int cx = w / 2;
            int cy = h / 2;

            for (int i = 0; i < 200; i++) {
                double t = time * 1.4 + i * 0.08;

                int x = cx + (int)(Math.sin(t * 2) * 160);
                int y = cy + (int)(Math.sin(t * 3 + 0.5) * 110);

                float k = i / 200f;
                g2.setColor(new Color(
                        (int)(200 + 55 * k),
                        (int)(120 + 135 * k),
                        255,
                        (int)(255 * k)
                ));

                g2.fillOval(x, y, 7, 7);
            }

            g2.dispose();
        }


        // Логіка частинок
        private void spawnParticles(int w, int h) {
            if (particles.size() < MAX_PARTICLES) {
                Particle p = new Particle();
                p.x = w / 2;
                p.y = h / 2;

                double angle = Math.random() * Math.PI * 2;
                double speed = 1 + Math.random() * 2;

                p.vx = Math.cos(angle) * speed;
                p.vy = Math.sin(angle) * speed;

                p.alpha = 1.0f;
                particles.add(p);
            }
        }

        private void updateParticles(int w, int h) {
            for (Particle p : particles) {

                // шум руху
                p.vx += Math.sin(time + p.x * 0.01) * 0.05;
                p.vy += Math.cos(time * 0.7 + p.y * 0.01) * 0.05;

                p.x += p.vx;
                p.y += p.vy;

                // затухання
                p.alpha -= 0.01f;
            }

            particles.removeIf(p -> p.alpha <= 0);
        }

        public void step() {
            time += 0.05;
            repaint();
        }

        public void reset() {
            time = 0;
            particles.clear();
            repaint();
        }

    }


    //Панель для біжучого рядка
    private static class MarqueePanel extends JPanel {
        private String text = "  Мультипотоковий застосунок – приклад біжучого рядка  ";
        private int xOffset = 0;

        public void step() {
            xOffset -= 3;
            int textWidth = getFontMetrics(getFont()).stringWidth(text);
            if (xOffset < -textWidth) {
                xOffset = getWidth();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(Color.BLACK);
            int y = getHeight() / 2 + getFontMetrics(getFont()).getAscent() / 2 - 2;
            g2.drawString(text, xOffset, y);
            g2.dispose();
        }
    }

    //Воркер анімації
    private class AnimationWorker implements Runnable, ControlledWorker {
        private final AnimationPanel panel;
        private Thread thread;
        private volatile boolean running = false;
        private volatile boolean paused = false;
        private volatile int delayMs = 40;

        private final ReentrantLock syncLock;
        private final Condition frameCondition;
        private final Flag frameFlag;

        public AnimationWorker(AnimationPanel panel,
                               ReentrantLock syncLock,
                               Condition frameCondition,
                               Flag frameFlag) {
            this.panel = panel;
            this.syncLock = syncLock;
            this.frameCondition = frameCondition;
            this.frameFlag = frameFlag;
        }

        @Override
        public void run() {
            while (running) {
                if (!paused) {
                    panel.step();
                    panel.repaint();

                    syncLock.lock();
                    try {
                        frameFlag.value = true;
                        frameCondition.signalAll();
                    } finally {
                        syncLock.unlock();
                    }
                }

                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                }
            }
        }

        @Override
        public void requestStart() {
            if (thread != null && thread.isAlive()) {
                return;
            }
            
            panel.reset();

            running = true;
            paused = false;

            thread = new Thread(this, "AnimationThread");
            thread.setDaemon(true);
            thread.start();
        }


        @Override
        public void requestStop() {
            running = false;
            if (thread != null) {
                thread.interrupt();
            }
        }

        @Override
        public void setPaused(boolean paused) {
            this.paused = paused;
        }

        @Override
        public void setDelay(int delayMs) {
            if (delayMs <= 0) delayMs = 1;
            this.delayMs = delayMs;
        }

        @Override
        public void setPriority(int prio) {
            if (thread != null) {
                thread.setPriority(prio);
            }
        }

        @Override
        public boolean isRunning() {
            return running;
        }

        @Override
        public boolean isPaused() {
            return paused;
        }


    }

    //Воркер обчислень
    private class ComputationWorker implements Runnable, ControlledWorker {
        private final JTextArea output;
        private Thread thread;
        private volatile boolean running = false;
        private volatile boolean paused = false;
        private volatile int delayMs = 150;

        // стан обчислень
        private long iterations = 0;
        private double sum = 0.0;

        // синхронізація з анімацією
        private final ReentrantLock syncLock;
        private final Condition frameCondition;
        private final Flag frameFlag;

        public ComputationWorker(JTextArea output,
                                 ReentrantLock syncLock,
                                 Condition frameCondition,
                                 Flag frameFlag) {
            this.output = output;
            this.syncLock = syncLock;
            this.frameCondition = frameCondition;
            this.frameFlag = frameFlag;
        }

        @Override
        public void run() {
            appendLine("Старт обчислення π за рядом Лейбніца...");
            while (running) {
                syncLock.lock();
                try {
                    while (!frameFlag.value && running) {
                        try {
                            frameCondition.await();
                        } catch (InterruptedException e) {
                            if (!running) {
                                break;
                            }
                        }
                    }
                    frameFlag.value = false;
                } finally {
                    syncLock.unlock();
                }

                if (!running) {
                    break;
                }

                if (paused) {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException ignored) {}
                    continue;
                }

                for (int i = 0; i < 500; i++) { // пакет обчислень
                    double term = 1.0 / (2 * iterations + 1);
                    if (iterations % 2 == 0) {
                        sum += term;
                    } else {
                        sum -= term;
                    }
                    iterations++;
                }
                double piApprox = 4 * sum;

                appendLine(String.format("Ітерацій: %d, π ≈ %.10f", iterations, piApprox));

                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                }
            }
            appendLine("Потік обчислень завершено.");
        }

        private void appendLine(String text) {
            SwingUtilities.invokeLater(() -> {
                output.append(text + "\n");
                String all = output.getText();
                if (all.length() > 8000) {
                    output.setText(all.substring(all.length() - 8000));
                }
                output.setCaretPosition(output.getDocument().getLength());
            });
        }

        @Override
        public void requestStart() {
            if (thread != null && thread.isAlive()) {
                return;
            }

            iterations = 0;
            sum = 0.0;
            appendLine("Стан обчислень скинуто. Новий старт...");

            running = true;
            paused = false;

            thread = new Thread(this, "ComputationThread");
            thread.setDaemon(true);
            thread.start();
        }


        @Override
        public void requestStop() {
            running = false;
            if (thread != null) {
                thread.interrupt();
            }
        }

        @Override
        public void setPaused(boolean paused) {
            this.paused = paused;
        }

        @Override
        public void setDelay(int delayMs) {
            if (delayMs <= 0) delayMs = 1;
            this.delayMs = delayMs;
        }

        @Override
        public void setPriority(int prio) {
            if (thread != null) {
                thread.setPriority(prio);
            }
        }

        @Override
        public boolean isRunning() {
            return running;
        }

        @Override
        public boolean isPaused() {
            return paused;
        }
    }

    //Воркер біжучого рядка
    private class MarqueeWorker implements Runnable, ControlledWorker {
        private final MarqueePanel panel;
        private Thread thread;
        private volatile boolean running = false;
        private volatile boolean paused = false;
        private volatile int delayMs = 40;

        public MarqueeWorker(MarqueePanel panel) {
            this.panel = panel;
        }

        @Override
        public void run() {
            while (running) {
                if (!paused) {
                    panel.step();
                    panel.repaint();
                }
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                }
            }
        }

        @Override
        public void requestStart() {
            if (thread != null && thread.isAlive()) {
                return;
            }
            running = true;
            paused = false;
            thread = new Thread(this, "MarqueeThread");
            thread.setDaemon(true);
            thread.start();
        }

        @Override
        public void requestStop() {
            running = false;
            if (thread != null) {
                thread.interrupt();
            }
        }

        @Override
        public void setPaused(boolean paused) {
            this.paused = paused;
        }

        @Override
        public void setDelay(int delayMs) {
            if (delayMs <= 0) delayMs = 1;
            this.delayMs = delayMs;
        }

        @Override
        public void setPriority(int prio) {
            if (thread != null) {
                thread.setPriority(prio);
            }
        }

        @Override
        public boolean isRunning() {
            return running;
        }

        @Override
        public boolean isPaused() {
            return paused;
        }
    }

    private static class Flag {
        boolean value = false;
    }

    private final AnimationPanel animationPanel = new AnimationPanel();
    private final JTextArea computationArea = new JTextArea(10, 30);
    private final MarqueePanel marqueePanel = new MarqueePanel();

    private final ReentrantLock syncLock = new ReentrantLock();
    private final Condition frameCondition = syncLock.newCondition();
    private final Flag frameFlag = new Flag();

    private final AnimationWorker animationWorker =
            new AnimationWorker(animationPanel, syncLock, frameCondition, frameFlag);
    private final ComputationWorker computationWorker =
            new ComputationWorker(computationArea, syncLock, frameCondition, frameFlag);
    private final MarqueeWorker marqueeWorker =
            new MarqueeWorker(marqueePanel);

    public MultiThreadDemo() {
        super("Демонстрація багатопотокового застосунку");

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 700);
        setLocationRelativeTo(null);

        setLayout(new BorderLayout(10, 10));

        animationPanel.setPreferredSize(new Dimension(600, 400));
        animationPanel.setBorder(BorderFactory.createTitledBorder("Анімація графіка / зображення"));
        add(animationPanel, BorderLayout.CENTER);

        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBorder(BorderFactory.createTitledBorder("Потік обчислень (π)"));

        computationArea.setEditable(false);
        computationArea.setLineWrap(true);
        computationArea.setWrapStyleWord(true);
        JScrollPane scroll = new JScrollPane(computationArea);
        rightPanel.add(scroll, BorderLayout.CENTER);

        add(rightPanel, BorderLayout.EAST);

        marqueePanel.setPreferredSize(new Dimension(1000, 60));
        marqueePanel.setBorder(BorderFactory.createTitledBorder("Біжучий рядок"));
        add(marqueePanel, BorderLayout.SOUTH);

        add(createControlPanel(), BorderLayout.NORTH);
    }

    private JPanel createControlPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new GridLayout(3, 1, 5, 5));
        panel.setBorder(BorderFactory.createTitledBorder("Керування потоками"));

        panel.add(createControlsFor("Потік анімації", animationWorker));
        panel.add(createControlsFor("Потік обчислень", computationWorker));
        panel.add(createControlsFor("Біжучий рядок", marqueeWorker));

        return panel;
    }

    private JPanel createControlsFor(String title, ControlledWorker worker) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT));
        p.add(new JLabel(title + ": "));

        JButton startBtn = new JButton("Старт");
        JButton stopBtn = new JButton("Стоп");
        JButton pauseBtn = new JButton("Пауза");

        // Пріоритети
        JComboBox<String> priorityBox = new JComboBox<>(new String[]{"MIN", "NORM", "MAX"});
        priorityBox.setSelectedIndex(1);

        // Затримка
        JLabel delayLabel = new JLabel("Затримка (мс):");
        JSpinner delaySpinner = new JSpinner(new SpinnerNumberModel(40, 1, 2000, 10));

        startBtn.addActionListener(e -> worker.requestStart());
        stopBtn.addActionListener(e -> worker.requestStop());

        pauseBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                boolean newPaused = !worker.isPaused();
                worker.setPaused(newPaused);

                if (newPaused) {
                    pauseBtn.setText("Продовжити");
                } else {
                    pauseBtn.setText("Пауза");
                }
            }
        });


        priorityBox.addActionListener(e -> {
            String sel = (String) priorityBox.getSelectedItem();
            int prio = Thread.NORM_PRIORITY;
            if ("MIN".equals(sel)) prio = Thread.MIN_PRIORITY;
            if ("MAX".equals(sel)) prio = Thread.MAX_PRIORITY;
            worker.setPriority(prio);
        });

        delaySpinner.addChangeListener(e -> {
            int delay = (Integer) delaySpinner.getValue();
            worker.setDelay(delay);
        });

        p.add(startBtn);
        p.add(stopBtn);
        p.add(pauseBtn);
        p.add(new JLabel("Пріоритет:"));
        p.add(priorityBox);
        p.add(delayLabel);
        p.add(delaySpinner);

        return p;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            MultiThreadDemo frame = new MultiThreadDemo();
            frame.setVisible(true);
        });
    }
}
