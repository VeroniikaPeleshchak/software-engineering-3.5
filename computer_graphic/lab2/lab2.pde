ArrayList<PVector> points;
int maxPoints = 30; 
float step = 0.01;  

void setup() {
  size(800, 600);
  points = new ArrayList<PVector>();
}

void draw() {
  background(250);

  fill(0);
  textSize(14);
  text("Клік мишкою — додати точку | 'cmd + R' — очистити | 'cmd + Z' — видалити останню", 10, height - 10);

  if (points.size() >= maxPoints) {
    fill(255, 50, 50);
    text("Досягнуто максимум точок (" + maxPoints + ")", 10, 20);
  }

  stroke(0);
  fill(0);
  for (PVector p : points) {
    ellipse(p.x, p.y, 8, 8);
  }


  if (points.size() > 1) {
    noFill();
    stroke(150, 150, 255);
    beginShape();
    for (PVector p : points) {
      vertex(p.x, p.y);
    }
    endShape();
  }

  if (points.size() >= 2) {
    stroke(255, 0, 0);
    noFill();
    beginShape();
    for (float t = 0; t <= 1.0001; t += step) {
      PVector p = deCasteljau(points, t);
      vertex(p.x, p.y);
    }
    endShape();
  } else {
    fill(120);
    text("Додай щонайменше дві точки, щоб побачити криву.", 10, 40);
  }
}

PVector deCasteljau(ArrayList<PVector> pts, float t) {
  ArrayList<PVector> temp = new ArrayList<PVector>();
  for (PVector p : pts) temp.add(p.copy());

  for (int r = 1; r < pts.size(); r++) {
    for (int i = 0; i < pts.size() - r; i++) {
      float x = (1 - t) * temp.get(i).x + t * temp.get(i + 1).x;
      float y = (1 - t) * temp.get(i).y + t * temp.get(i + 1).y;
      temp.set(i, new PVector(x, y));
    }
  }
  return temp.get(0);
}

void mousePressed() {
  if (points.size() < maxPoints && mouseX >= 0 && mouseX <= width && mouseY >= 0 && mouseY <= height) {
    points.add(new PVector(mouseX, mouseY));
  }
}

void keyPressed() {
  if (key == 'r' || key == 'R') {
    points.clear();
  }
  if ((key == 'z' || key == 'Z') && points.size() > 0) {
    points.remove(points.size() - 1);
  }
}
