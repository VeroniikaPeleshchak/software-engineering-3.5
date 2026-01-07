class Window {
  float x1, y1, x2, y2;
  int priority;
  Window(float a, float b, float c, float d, int p) {
    x1=a; y1=b; x2=c; y2=d; priority=p;
  }
}

class Segment {
  float x1, y1, x2, y2;
  Segment(float a, float b, float c, float d) {
    x1=a; y1=b; x2=c; y2=d;
  }
}

ArrayList<Window> windows = new ArrayList<>();
ArrayList<Segment> segments = new ArrayList<>();

ArrayList<Window> originalWindows = new ArrayList<>();
ArrayList<Segment> originalSegments = new ArrayList<>();

ArrayList<PVector> midpoints = new ArrayList<>();

int mode = 4;  // 1=original, 2=windows, 3=midpoints, 4=final
boolean randomMode = false;

float uiW = 260;
float uiH = 110;


void setup() {
  size(900, 700);

  //ПОЧАТКОВІ СТАЛІ ВІКНА
  originalWindows.add(new Window(100, 100, 300, 300, 1));
  originalWindows.add(new Window(250, 150, 500, 400, 2));
  originalWindows.add(new Window(150, 250, 450, 550, 3));

  //ПОЧАТКОВІ СТАЛІ ЛІНІЇ
  originalSegments.add(new Segment(50, 150, 600, 500));
  originalSegments.add(new Segment(60, 30, 500, 220));
  originalSegments.add(new Segment(700, 50, 20, 550));
  originalSegments.add(new Segment(200, 10, 200, 590));

  restoreOriginal();
}



void restoreOriginal() {
  randomMode = false;
  windows = new ArrayList<Window>();
  segments = new ArrayList<Segment>();

  for (Window w : originalWindows)
    windows.add(new Window(w.x1, w.y1, w.x2, w.y2, w.priority));

  for (Segment s : originalSegments)
    segments.add(new Segment(s.x1, s.y1, s.x2, s.y2));

  midpoints.clear();
}



void draw() {
  background(255);

  drawUI();

  if (mode >= 2) drawWindows();
  if (mode >= 1) drawOriginalLines();
  if (mode >= 3) drawMidpoints();
  if (mode >= 4) drawClipped();
}


void drawUI() {
  float x = width - uiW - 20;
  float y = height - uiH - 20;

  fill(255, 255, 255, 180);
  noStroke();
  rect(x, y, uiW, uiH, 12);

  fill(0);
  textSize(14);

  text("Midpoint Subdivision", x+15, y+25);
  text("Режим: " + (randomMode ? "RANDOM" : "ORIGINAL"), x+15, y+47);
  text("SPACE – змінити вигляд", x+15, y+67);
  text("G – random mode (генерація)", x+15, y+87);
  text("O – original mode", x+15, y+107);
}



void drawOriginalLines() {
  stroke(140);
  strokeWeight(1.5);
  for (Segment s : segments)
    line(s.x1, s.y1, s.x2, s.y2);
}


void drawWindows() {
  noFill();
  for (Window w : windows) {
    strokeWeight(1 + (4 - w.priority));
    stroke(0);
    rect(w.x1, w.y1, w.x2 - w.x1, w.y2 - w.y1);
    text("P" + w.priority, w.x1 + 5, w.y1 + 15);
  }
}


void drawMidpoints() {
  fill(0, 120, 255);
  noStroke();
  for (PVector p : midpoints)
    ellipse(p.x, p.y, 5, 5);
}



void drawClipped() {
  windows.sort((a, b) -> a.priority - b.priority);
  for (Segment s : segments)
    clipByPriority(s.x1, s.y1, s.x2, s.y2);
}


void clipByPriority(float x1, float y1, float x2, float y2) {
  for (Window w : windows)
    if (clipMid(x1, y1, x2, y2, w))
      return;
}


boolean clipMid(float x1, float y1, float x2, float y2, Window w) {
  int c1 = outcode(x1, y1, w);
  int c2 = outcode(x2, y2, w);

  if (c1 == 0 && c2 == 0) {
    stroke(0, 180, 0);
    strokeWeight(2);
    line(x1, y1, x2, y2);
    return true;
  }

  if ((c1 & c2) != 0) {
    stroke(220, 0, 0, 80);
    line(x1, y1, x2, y2);
    return false;
  }

  float mx = (x1 + x2) * 0.5;
  float my = (y1 + y2) * 0.5;

  midpoints.add(new PVector(mx, my));

  if (dist(x1, y1, x2, y2) < 1)
    return false;

  boolean a = clipMid(x1, y1, mx, my, w);
  boolean b = clipMid(mx, my, x2, y2, w);

  return a || b;
}


int LEFT = 1, RIGHT = 2, BOTTOM = 4, TOP = 8;

int outcode(float x, float y, Window w) {
  int c = 0;
  if (x < w.x1) c |= LEFT;
  else if (x > w.x2) c |= RIGHT;
  if (y < w.y1) c |= TOP;
  else if (y > w.y2) c |= BOTTOM;
  return c;
}


boolean rectOverlap(Window a, Window b) {
  return !(a.x2 <= b.x1 || a.x1 >= b.x2 || a.y2 <= b.y1 || a.y1 >= b.y2);
}

void generateWindows(int count) {
  windows.clear();

  int attempts = 0;
  while (windows.size() < count && attempts < 500) {
    attempts++;

    float w = random(120, 220);
    float h = random(120, 220);

    float x = random(40, width - w - uiW - 40);
    float y = random(40, height - h - uiH - 40);

    Window nw = new Window(x, y, x+w, y+h, windows.size()+1);

    boolean ok = true;
    for (Window other : windows)
      if (rectOverlap(nw, other)) ok = false;

    if (ok) windows.add(nw);
  }
}


void generateSegments(int count) {
  segments.clear();

  for (int i = 0; i < count; i++) {
    float x1, y1, x2, y2;

    do {
      x1 = random(width - uiW - 50);
      y1 = random(height - uiH - 50);
      x2 = random(width - uiW - 50);
      y2 = random(height - uiH - 50);
    } while (dist(x1, y1, x2, y2) < 80);

    segments.add(new Segment(x1, y1, x2, y2));
  }
}


void keyPressed() {

  //Перемикання вигляду
  if (key == ' ') {
    mode++;
    if (mode > 4) mode = 1;
  }

  //G — RANDOM MODE
  if (key == 'g' || key == 'G') {
    randomMode = true;
    generateWindows(3);
    generateSegments(5);
    midpoints.clear();
  }

  //O — ORIGINAL MODE
  if (key == 'o' || key == 'O') {
    restoreOriginal();
  }
}
