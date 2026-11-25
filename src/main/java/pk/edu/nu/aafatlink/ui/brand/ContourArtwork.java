package pk.edu.nu.aafatlink.ui.brand;

import javafx.scene.canvas.Canvas;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;

/** Decorative, fictional terrain used in the AafatLink identity. */
public final class ContourArtwork extends Region {
    private static final int STEPS = 120;
    private final Canvas canvas = new Canvas();

    public ContourArtwork() {
        canvas.setManaged(false);
        canvas.setMouseTransparent(true);
        getChildren().add(canvas);
    }

    @Override
    protected void layoutChildren() {
        double width = getWidth();
        double height = getHeight();
        if (width == canvas.getWidth() && height == canvas.getHeight()) {
            return;
        }
        canvas.setWidth(width);
        canvas.setHeight(height);
        var pen = canvas.getGraphicsContext2D();
        pen.clearRect(0, 0, width, height);
        pen.save();
        pen.translate(width * 0.63, height * 0.58);
        double scale = Math.max(width / 320, height / 210);
        pen.scale(scale, scale);
        pen.setLineWidth(0.8 / scale);
        for (int index = 0; index < 21; index++) {
            double radius = 21 + index * 10;
            boolean majorContour = index % 5 == 0;
            pen.setStroke(majorContour ? Color.web("#E2AC7A", 0.66) : Color.web("#B8BCCE", 0.39));
            pen.beginPath();
            for (int step = 0; step <= STEPS; step++) {
                double angle = step * 2 * Math.PI / STEPS;
                double ridge = 1 + 0.22 * Math.sin(3 * angle + radius / 75)
                        + 0.12 * Math.cos(5 * angle - radius / 110);
                double x = radius * ridge * Math.cos(angle);
                double y = radius * ridge * Math.sin(angle) * 0.76;
                if (step == 0) {
                    pen.moveTo(x, y);
                } else {
                    pen.lineTo(x, y);
                }
            }
            pen.closePath();
            pen.stroke();
        }
        pen.restore();
    }
}
