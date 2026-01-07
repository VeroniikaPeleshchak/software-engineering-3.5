import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.Random;

public class SpiralGraph extends JPanel {
    private Color graphColor = Color.RED;
    private float strokeWidth = 2.0f;
    private float[] dashPattern = null;
    private Random rand = new Random();

    public SpiralGraph() {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                graphColor = new Color(rand.nextInt(256), rand.nextInt(256), rand.nextInt(256));
                strokeWidth = 1.0f + rand.nextFloat() * 4.0f;
                dashPattern = rand.nextBoolean() ? null : new float[]{5.0f, 5.0f};
                repaint();
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int width = getWidth();
        int height = getHeight();

        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(2));
        g2.drawLine(width / 2, 0, width / 2, height);
        g2.drawLine(0, height / 2, width, height / 2);

        g2.setFont(new Font("Arial", Font.PLAIN, 14));
        g2.drawString("Пелещак Вероніка, Варіант 15", 10, 20);

        if (dashPattern != null) {
            g2.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, dashPattern, 0));
        } else {
            g2.setStroke(new BasicStroke(strokeWidth));
        }
        g2.setColor(graphColor);

        double m = 6.0 / 7.0;
        double a = 5.0;
        int centerX = width / 2;
        int centerY = height / 2;
        double scale = Math.min(width, height) / 15.0; 

        Path2D.Double path = new Path2D.Double();
        boolean first = true;
        for (double phi = 0; phi <= 20 * Math.PI; phi += 0.01) {
            double r = Math.pow(a, m) * Math.cos(m * phi);
            double x = r * Math.cos(phi) * scale;
            double y = r * Math.sin(phi) * scale;

            if (first) {
                path.moveTo(centerX + x, centerY - y);
                first = false;
            } else {
                path.lineTo(centerX + x, centerY - y);
            }
        }

        g2.draw(path);
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Синусоїдна спіраль");
        SpiralGraph panel = new SpiralGraph();
        frame.add(panel);
        frame.setSize(800, 600);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}
